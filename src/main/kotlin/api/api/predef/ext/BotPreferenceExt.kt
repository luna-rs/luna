package api.predef.ext

import api.predef.*
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.player.item.consume.food.Food
import game.skill.mining.Pickaxe
import game.skill.woodcutting.cutTree.Axe
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.def.WantedItemDefinition.WantedItemTag
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotPreference

/**
 * Returns the bot's total ownership of an item across all tracked containers.
 *
 * Noted and unnoted variants are combined using [itemTracker], allowing ownership goals to account for both
 * representations of the same item.
 *
 * @param id The item id to count.
 * @return The total number of owned items across all tracked containers.
 */
fun Bot.totalItemAmount(id: Int): Int {
    val definition = itemDef(id)

    // Wanted items are normalized to their unnoted id, but this also makes the helper safe when passed a noted id.
    val unnotedId = definition.unnotedId.orElse(id)
    val unnotedDefinition = itemDef(unnotedId)

    var amount = itemTracker.count(unnotedId)

    // Add the noted stack, if one exists, so 100 banked logs and 50 noted logs count as 150 owned logs.
    val notedId = unnotedDefinition.notedId
    if (notedId.isPresent && notedId.asInt != unnotedId) {
        amount += itemTracker.count(notedId.asInt)
    }

    return amount
}

/**
 * Reconciles the bot's wanted items against its current ownership and skill levels.
 *
 * Items with invalid targets or exceeded skill-level restrictions are marked for removal. Temporary wanted
 * items, identified by `min == -1`, are also removed once their ownership targets are satisfied.
 *
 * Persistent wanted items remain available for future replenishment. Removal is performed
 * through [BotPreference.removeWantedItem], which may protect default wanted items.
 */
fun BotPreference.rebalanceWantedItems() {
    val remove = ArrayList<Int>()
    for (wanted in wantedItems) {
        // A non-positive target cannot represent a useful ownership goal.
        val invalidTarget = wanted.target <= 0

        // A level-restricted wanted item becomes permanently irrelevant once the bot exceeds its maximum level.
        val exceededMaxLevel = wanted.skill != -1 &&
                wanted.maxLevel != -1 &&
                bot.skills.getSkill(wanted.skill).staticLevel > wanted.maxLevel

        // Temporary requirements disappear once the bot owns the requested total amount.
        val temporarySatisfied = wanted.min == -1 &&
                bot.totalItemAmount(wanted.id) >= wanted.target

        if (invalidTarget || exceededMaxLevel || temporarySatisfied) {
            remove += wanted.id
        }
    }

    // Remove after iteration so we never structurally modify the map while its iterator is active.
    remove.forEach { removeWantedItem(it) }
}

/**
 * Finds wanted items that the bot has not yet acquired in their desired quantities.
 *
 * First calls [rebalanceWantedItems] to remove obsolete or satisfied definitions. Remaining items are filtered by
 * ownership, skill restrictions, and the bot's preferred activities. Merchants bypass the activity-based item
 * restrictions.
 *
 * Persistent items below their minimum ownership are treated as urgent. Results are ordered by descending item
 * priority, with urgent items appearing first when priorities are equal. Equipment the bot cannot yet use
 * follows usable items, without removing future upgrades from its wants.
 *
 * @return Wanted-item definitions that are eligible for acquisition.
 */
fun BotPreference.wantedItemsToAcquire(): List<WantedItemDefinition> {
    rebalanceWantedItems()

    val urgent = ArrayList<WantedItemDefinition>()
    val normal = ArrayList<WantedItemDefinition>()

    for (wanted in wantedItems) {
        val owned = bot.totalItemAmount(wanted.id)

        // The target always represents desired total ownership.
        if (owned >= wanted.target) {
            continue
        }

        if (!isMerchant) {
            val skilling = WantedItemTag.SKILLING_CONSUMABLE in wanted.tags ||
                    WantedItemTag.TOOL in wanted.tags ||
                    WantedItemTag.COSMETIC_EQUIPMENT in wanted.tags
            val combat = WantedItemTag.COMBAT_CONSUMABLE in wanted.tags ||
                    WantedItemTag.WEAPON in wanted.tags ||
                    WantedItemTag.COMBAT_EQUIPMENT in wanted.tags
            when {
                isSkiller && !skilling -> continue
                isPker && skilling && !combat -> continue
            }

            // Only skill supplies depend on preferred skills; food and shared combat supplies remain useful.
            if (wanted.skill != -1 && skilling && !combat && wanted.skill !in skills) {
                continue
            }
        }

        // A missing skill means the maximum refers to combat level, as in rebalancing.
        val level = if (wanted.skill == -1) bot.combatLevel else bot.skills.getSkill(wanted.skill).staticLevel
        if (wanted.maxLevel != -1 && level > wanted.maxLevel) {
            continue
        }

        if (wanted.min != -1 && owned < wanted.min) {
            // Being below the persistent minimum is more important than merely being below the preferred target.
            urgent += wanted
        } else {
            normal += wanted
        }
    }

    // Keep shopping order natural between bots while still preserving minimum-stock urgency.
    return (urgent + normal).sortedWith(compareByDescending<WantedItemDefinition> { bot.canUseWantedItem(it.id) }
                                            .thenByDescending { it.priority.level })
}

/**
 * Checks known tool and equipment requirements without attempting to equip or use the item.
 * Axes and pickaxes can be used from inventory without meeting their wielding requirements.
 * Items without known requirements remain eligible as supplies or merchant stock.
 */
fun Bot.canUseWantedItem(id: Int): Boolean {
    Pickaxe.ID_TO_PICKAXE[id]?.let { return mining.staticLevel >= it.level }
    Axe.values().firstOrNull { it.id == id }?.let { return woodcutting.staticLevel >= it.level }
    return EquipmentDefinition.ALL.get(id).map { it.meetsAllRequirements(this) }.orElse(true)
}

/**
 * Finds wanted items whose owned quantities exceed the bot's retained stock requirements.
 *
 * The retained amount is the greater of the item's target and minimum. Additional surplus thresholds are determined
 * by merchanting preferences and may be increased for items used in the bot's preferred skilling or combat
 * activities.
 *
 * Only items exceeding their surplus threshold and having an economy price are included. Each result contains the
 * wanted-item definition and the quantity available above retention, ordered by descending total surplus economy value.
 *
 * @return Pairs containing eligible wanted items and their surplus quantities.
 */
fun BotPreference.wantedItemsToTrim(): List<Pair<WantedItemDefinition, Int>> {
    val candidates = ArrayList<Pair<WantedItemDefinition, Int>>()
    for (item in wantedItems) {
        val target = item.target.coerceAtLeast(0).toLong()
        val retained = maxOf(target, item.min.toLong())
        val surplus = bot.totalItemAmount(item.id).toLong() - retained
        var surplusTarget = when {
            lovesActivity(BotActivity.MERCHANTING) -> 0L
            likesActivity(BotActivity.MERCHANTING) -> target / 2
            hatesActivity(BotActivity.MERCHANTING) -> target * 2
            else -> target
        }
        val skilling = WantedItemTag.SKILLING_CONSUMABLE in item.tags ||
                WantedItemTag.TOOL in item.tags || WantedItemTag.COSMETIC_EQUIPMENT in item.tags
        val combat = WantedItemTag.COMBAT_CONSUMABLE in item.tags ||
                WantedItemTag.WEAPON in item.tags || WantedItemTag.COMBAT_EQUIPMENT in item.tags
        val level = if (item.skill == -1) bot.combatLevel else bot.skills.getSkill(item.skill).staticLevel
        val relevant = item.maxLevel == -1 || level <= item.maxLevel
        if (!isMerchant && relevant) {
            // Favored supplies require more surplus before a sale, but are never permanently blocked from sale.
            if ((isSkiller && skilling) || (isPker && combat)) {
                surplusTarget = maxOf(surplusTarget, target * 4)
            }
            if ((lovesActivity(BotActivity.PROFIT_SKILLS) && skilling) ||
                (lovesActivity(BotActivity.PROFIT_COMBAT) && combat)
            ) {
                surplusTarget = maxOf(surplusTarget, target * 2)
            }
        }
        if (surplus > surplusTarget && world.economy.prices[item.id] != null) {
            candidates += item to surplus.toInt()
        }
    }
    return candidates.sortedByDescending { (item, surplus) -> world.economy.getRealPrice(item.id) * surplus.toDouble() }
}

/**
 * Ensures the bot has a wanted-item target for a required quantity.
 *
 * If the bot already owns the requested amount, no changes are made. Otherwise, a temporary
 * wanted item is created when no definition exists, or an existing target is raised without
 * replacing its other metadata.
 *
 * @param id The required item id.
 * @param amount The required total ownership quantity.
 * @return `true` if the bot already owns the required amount, otherwise `false`.
 */
fun BotPreference.requireItem(id: Int, amount: Int): Boolean {
    if (bot.totalItemAmount(id) >= amount) {
        return true
    }

    val existing = getWantedItem(id)
    if (existing == null) {
        // No long-term definition exists, so create a temporary requirement.
        addWantedItem(id, amount)
    } else if (amount > existing.target) {
        // Do not replace persistent min/skill/max-level metadata just because another script needs more of the item.
        raiseWantedItemTarget(id, amount)
    }

    return false
}

/**
 * Returns the food types this bot considers suitable based on its combat level and personality.
 *
 * The initial food tier is determined by combat level:
 * - Below 25: tier 0, healing at most 3 hitpoints.
 * - 25 to 49: tier 1, healing 4 to 7 hitpoints.
 * - 50 to 89: tier 2, healing 8 to 12 hitpoints.
 * - 90 or higher: tier 3, healing 13 to 22 hitpoints.
 *
 * Bots with the `isDumb` personality flag select one tier below their initial tier.
 * Only food within the selected tier's healing range is included. Ownership and affordability are not checked.
 *
 * @return A list of food types matching the selected tier, or an empty list if none match.
 * @throws IllegalStateException If the selected tier is outside the supported range of 0 to 3.
 */
fun BotPreference.allSuitableFood(): List<Food> {
    val all = Food.ID_TO_FOOD.values
    var tier = when {
        bot.combatLevel < 25 -> 0
        bot.combatLevel < 50 -> 1
        bot.combatLevel < 90 -> 2
        else -> 3
    }
    if (bot.personality.isDumb) {
        tier--
    }
    return when (tier) {
        0 -> all.filter { it.heal <= 3 }
        1 -> all.filter { it.heal in 4..7 }
        2 -> all.filter { it.heal in 8..12 }
        3 -> all.filter { it.heal in 13..22 }
        else -> throw IllegalStateException("Invalid tier $tier.")
    }
}