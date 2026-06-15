package engine.bot.gear

import api.predef.*
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.HashMultimap
import engine.bot.gear.BotGearSelector.ALL_GEAR
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.def.AmmoDefinition
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.WeaponDefinition
import io.luna.game.model.item.Equipment.*
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.Weapon

/**
 * Resolves bot equipment layouts from known gear definitions and the items currently owned by a bot.
 *
 * This object is the decision layer of the bot gear system. It does not withdraw, move, or equip items directly.
 * Instead, it builds a desired equipment layout from:
 * - The bot's tracked owned items.
 * - Equipment requirements.
 * - Full gear sets.
 * - Slot-specific gear groups.
 * - Requested gear purposes.
 * - Simple per-slot usefulness scoring.
 *
 * Selection methods return a [BotGearSelectorFill] so scripts can further refine the selected layout before building a
 * [BotGearLocator]. The locator is responsible for physically locating and equipping the selected items.
 *
 * @author lare96
 */
object BotGearSelector {

    /**
     * All known bot gear candidates, indexed by equipment slot.
     *
     * This registry is built once and reused by gear selection. It combines full gear set items and slot-specific gear
     * groups into one lookup table where each equipment slot maps to the known candidate items for that slot.
     *
     * Full set items are included so set pieces can participate in generic purpose-based selection. Slot-specific groups
     * are then added for amulets, boots, capes, gloves, rings, shields, and weapons.
     */
    val ALL_GEAR = lazyVal {
        val map = HashMultimap.create<Int, BotGearItem>()

        for (set in BotGearSet.entries) {
            set.equipment.forEach {
                val index = equipDef(it).index
                map.put(index, BotGearItem(index, it, set.purposes, set.priority()))
            }
        }

        AmuletBotGear.entries.forEach { map.putAll(AMULET, it.items()) }
        BootsBotGear.entries.forEach { map.putAll(BOOTS, it.items()) }
        CapeBotGear.entries.forEach { map.putAll(CAPE, it.items()) }
        GlovesBotGear.entries.forEach { map.putAll(HANDS, it.items()) }
        RingBotGear.entries.forEach { map.putAll(RING, it.items()) }
        ShieldBotGear.entries.forEach { map.putAll(SHIELD, it.items()) }
        WeaponBotGear.entries.forEach { map.putAll(WEAPON, it.items()) }
        EquipmentDefinition.ALL.filter { it.index == WEAPON }.forEach {
            map.put(WEAPON, BotGearItem(WEAPON, it.id, setOf(BotGearPurpose.MELEE, BotGearPurpose.PKING), 0))
        }
        map
    }

    /**
     * A mutable builder for refining a resolved equipment layout.
     *
     * Selection methods return this builder instead of a locator directly so scripts can start with a generic selection,
     * specific gear set, or exact requested layout, then apply extra fallback or replacement rules before equipping.
     *
     * Purpose-based filling only affects empty slots. This preserves gear that was already selected by a set, exact item
     * request, or previous fill call.
     *
     * @param bot The bot whose ownership and equipment requirements are being checked.
     * @param equipment The desired equipment layout, indexed by equipment slot.
     */
    class BotGearSelectorFill(val bot: Bot, val equipment: Array<Int?>) {

        /**
         * Attempts to fill every empty equipment slot with gear matching the supplied purposes.
         *
         * Each slot is passed to [fill]. Existing slot values are preserved, so this method is safe to call after
         * selecting a gear set, exact requested layout, or generic purpose-based layout.
         *
         * @param purposes The purposes each fallback gear item must satisfy.
         * @param excluding A predicate used to reject specific gear candidates.
         *
         * @return This builder.
         */
        fun fillAll(
            purposes: Set<BotGearPurpose>,
            excluding: (BotGearItem) -> Boolean = { false }
        ): BotGearSelectorFill {
            equipment.indices.forEach { fill(it, purposes, excluding) }
            return this
        }

        /**
         * Attempts to fill one empty equipment slot with the best gear matching the supplied purposes.
         *
         * This method does nothing if the slot already has a selected item. Otherwise, candidates are pulled from
         * [ALL_GEAR] for the requested slot. A candidate is accepted only when:
         *
         * - The bot owns the item.
         * - The bot meets the item's equipment requirements.
         * - The item's real equipment slot matches [index].
         * - The candidate satisfies all requested [purposes].
         * - The candidate is not rejected by [excluding].
         *
         * All accepted candidates are scored, sorted from highest to lowest score, and the highest-scoring item is placed
         * into [equipment] at [index].
         *
         * @param index The equipment slot to fill.
         * @param purposes The purposes the selected fallback item must satisfy.
         * @param excluding A predicate used to reject specific gear candidates.
         * @return This builder.
         */
        fun fill(
            index: Int,
            purposes: Set<BotGearPurpose>,
            excluding: (BotGearItem) -> Boolean = { false }
        ): BotGearSelectorFill {
            if (equipment[index] == null) {
                val selected = ArrayList<BotGearItem>()
                val indexGear = ALL_GEAR.value[index]

                for (item in indexGear) {
                    if (valid(bot, index, item.id) && item.purposes.containsAll(purposes) && !excluding(item)) {
                        selected += item
                    }
                }

                selected.sortByDescending { equipDef(it.id).calculateScore(it) }
                equipment[index] = selected.firstOrNull()?.id
            }
            return this
        }

        /**
         * Fills one equipment slot with a specific item if the slot is currently empty.
         *
         * The item is accepted only if the bot owns it, meets its equipment requirements, and the item belongs to the
         * requested equipment slot.
         *
         * @param index The equipment slot to fill.
         * @param id The item id to place in the slot.
         * @return This builder.
         */
        fun fill(index: Int, id: Int): BotGearSelectorFill {
            if (equipment[index] == null && valid(bot, index, id)) {
                equipment[index] = id
            }
            return this
        }

        /**
         * Replaces one equipment slot with a specific item.
         *
         * Unlike [fill], this method can overwrite an existing selected item. The replacement is accepted only if the
         * bot owns it, meets its equipment requirements, and the item belongs to the requested equipment slot.
         *
         * @param index The equipment slot to replace.
         * @param id The item id to place in the slot.
         * @return This builder.
         */
        fun replace(index: Int, id: Int): BotGearSelectorFill {
            if (valid(bot, index, id)) {
                equipment[index] = id
            }
            return this
        }

        /**
         * Fills an empty ammunition slot with valid ammo if possible, and if a ranged weapon is equipped.
         */
        private fun fillAmmo() {
            val weapon = equipment[WEAPON]
            if (equipment[AMMUNITION] == null && weapon != null && WeaponDefinition.ALL.get(weapon)
                    .filter { it.type == Weapon.SHORTBOW || it.type == Weapon.LONGBOW || it.type == Weapon.CROSSBOW }.isPresent
            ) {
                class AmmoSelection(val id: Int, val strength: Int)

                val selected = ArrayList<AmmoSelection>()
                for (ammoType in AmmoDefinition.ALL.values) {
                    if (ammoType.weapons.contains(weapon)) {
                        for (id in ammoType.ammo) {
                            if (valid(bot, AMMUNITION, id)) {
                                selected += AmmoSelection(id, ammoType.strength)
                            }
                        }
                    }
                }
                selected.sortByDescending { it.strength }
                equipment[AMMUNITION] = selected.firstOrNull()?.id
            }
        }

        /**
         * Builds a locator for the current desired equipment layout.
         *
         * @return A locator that can locate and equip the selected gear.
         */
        fun buildLocator(): BotGearLocator {
            fillAmmo()
            return BotGearLocator(bot, equipment)
        }
    }

    /**
     * Finds the best owned equipment matching the supplied purposes.
     *
     * This performs a generic purpose-based search across every known gear candidate. Valid candidates are grouped by
     * equipment slot, scored, and reduced to the highest-scoring item for each slot.
     *
     * The returned builder can be refined further before equipping. For example, a script can replace a weapon, fill
     * missing slots with a second purpose, or request a specific utility item before calling
     * [BotGearSelectorFill.buildLocator].
     *
     * @param bot The bot selecting gear.
     * @param purposes The purposes each selected gear item must satisfy.
     * @param excluding A predicate used to reject specific gear candidates.
     *
     * @return A mutable fill builder containing the selected equipment layout.
     *
     * @throws IllegalArgumentException If [purposes] is empty.
     */
    fun find(
        bot: Bot,
        purposes: Set<BotGearPurpose>,
        excluding: (BotGearItem) -> Boolean = { false }
    ): BotGearSelectorFill {
        require(purposes.isNotEmpty()) { "Must have at least one purpose." }

        val selection = ArrayListMultimap.create<Int, BotGearItem>()

        for (entry in ALL_GEAR.value.entries()) {
            val item = entry.value
            if (!valid(bot, entry.key, item.id) || !item.purposes.containsAll(purposes) || excluding(item)) {
                continue
            }
            selection.put(entry.key, item)
        }

        val equipment = arrayOfNulls<Int>(14)
        for (index in equipment.indices) {
            val items = selection[index]
            items.sortByDescending { equipDef(it.id).calculateScore(it) }
            equipment[index] = items.firstOrNull()?.id
        }

        return BotGearSelectorFill(bot, equipment)
    }

    /**
     * Finds the owned and equippable pieces of a specific gear set.
     *
     * Missing items, unowned items, and items the bot does not meet the requirements for are skipped. The returned
     * builder can then be used to fill missing slots with purpose-based fallback gear.
     *
     * @param bot The bot selecting gear.
     * @param set The gear set to select from.
     *
     * @return A mutable fill builder containing the valid owned pieces of [set].
     */
    fun find(bot: Bot, set: BotGearSet): BotGearSelectorFill {
        val equipment = arrayOfNulls<Int>(14)

        for (item in set.equipment) {
            val def = equipDef(item)
            if (valid(bot, def.index, item)) {
                equipment[def.index] = item
            }
        }

        return BotGearSelectorFill(bot, equipment)
    }

    /**
     * Finds the valid owned items from a requested equipment layout.
     *
     * This is used when a script wants to request exact item ids for one or more slots. Each non-null requested item is
     * accepted only if the bot owns it, meets its requirements, and the item belongs to the requested slot.
     *
     * @param bot The bot selecting gear.
     * @param requested The requested item ids, indexed by equipment slot.
     *
     * @return A mutable fill builder containing the valid owned items from [requested].
     */
    fun find(bot: Bot, requested: Array<Int?>): BotGearSelectorFill {
        val equipment = arrayOfNulls<Int>(14)

        requested.forEachIndexed { index, value ->
            if (value != null && valid(bot, index, value)) {
                equipment[index] = value
            }
        }

        return BotGearSelectorFill(bot, equipment)
    }

    /**
     * Calculates a rough usefulness score for this equipment definition.
     *
     * The score is intentionally simple. It starts with the item's highest equipment requirement, then applies extra
     * bonuses for weapon and shield types that are usually more desirable for bots.
     *
     * This score is used only to choose between multiple valid candidates in the same slot. It is not intended to be a
     * perfect combat formula.
     *
     * @return The calculated equipment score.
     */
    private fun EquipmentDefinition.calculateScore(item: BotGearItem): Int {
        val weaponType = if (index == WEAPON) WeaponDefinition.ALL[id].orElse(null)?.type else null
        val shieldType = if (index == SHIELD) ItemDefinition.ALL[id].orElse(null) else null

        var score = 0
        score += item.priority * 10
        score += highestRequirement
        score +=
            when (weaponType) {
                Weapon.WHIP -> 150
                Weapon.SCIMITAR, Weapon.SHORTBOW -> 100
                Weapon.DART, Weapon.KNIFE -> 75
                Weapon.LONGSWORD, Weapon.BATTLEAXE, Weapon.TWO_HANDED_SWORD, Weapon.CLAWS -> 50
                Weapon.SWORD, Weapon.DAGGER, Weapon.MACE -> 25
                else -> 0
            }

        score +=
            if (shieldType != null)
                when {
                    shieldType.name.contains("crystal", true) -> 50
                    shieldType.name.contains("kiteshield", true) -> 25
                    else -> 0
                }
            else 0

        return score
    }

    /**
     * Returns whether the bot can select an item for the requested equipment slot.
     *
     * An item is valid only when:
     *
     * - The bot owns the item according to [Bot.itemTracker].
     * - The bot meets all equipment requirements.
     * - The item's real equipment slot matches the requested [index].
     *
     * @param bot The bot being checked.
     * @param index The requested equipment slot.
     * @param id The item id to check.
     *
     * @return `true` if the item can be selected for [index], otherwise `false`.
     */
    private fun valid(bot: Bot, index: Int, id: Int): Boolean {
        val def = equipDef(id)
        return bot.itemTracker.contains(id) && def.meetsAllRequirements(bot) && index == def.index
    }
}