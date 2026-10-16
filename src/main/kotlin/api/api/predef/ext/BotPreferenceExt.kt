package api.predef.ext

import api.predef.*
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPreference

/**
 * Returns the bot's total ownership of [id] across every container tracked by [itemTracker].
 *
 * Noted and unnoted variants are combined because wanted-item definitions are ownership goals rather than container
 * or representation goals.
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
 * Reconciles wanted-item definitions against the bot's current total ownership and skill levels.
 *
 * Temporary wanted items use `min == -1`. They are removed once their total-ownership target has been satisfied.
 *
 * Persistent wanted items use a real minimum value. They remain in the preference map after reaching their target so
 * the bot can want them again later if its ownership falls.
 *
 * Definitions with an expired maximum skill level are removed completely because the bot has permanently outgrown
 * the item.
 */
fun BotPreference.rebalanceWantedItems() {
    val remove = ArrayList<Int>()
    val iterator = getWantedItems()

    while (iterator.hasNext()) {
        val wanted = iterator.next()

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
 * Returns wanted items that still require additional ownership.
 *
 * Persistent items below their minimum are placed first because they represent an urgent replenishment need.
 * Remaining wanted items are then returned in randomized order so bots do not always shop for identical items first.
 */
fun BotPreference.wantedItemsToAcquire(): List<WantedItemDefinition> {
    rebalanceWantedItems()

    val urgent = ArrayList<WantedItemDefinition>()
    val normal = ArrayList<WantedItemDefinition>()
    val iterator = getWantedItems()

    while (iterator.hasNext()) {
        val wanted = iterator.next()
        val owned = bot.totalItemAmount(wanted.id)

        // The target always represents desired total ownership.
        if (owned >= wanted.target) {
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
    return (urgent + normal).sortedBy { it.priority.level }
}

/**
 * Ensures this bot eventually owns at least [amount] of [id].
 *
 * If the requirement is not already satisfied, a temporary wanted item is created. Existing wanted-item metadata is
 * preserved, and an existing target may only be raised.
 *
 * @param id The required item id.
 * @param amount The required total amount.
 * @return `true` if the bot already owns at least [amount], otherwise `false`.
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