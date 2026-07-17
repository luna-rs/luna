package engine.bot.gear

import api.predef.*
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.HashMultimap
import engine.bot.gear.BotGearSelector.ALL_GEAR
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.def.AmmoDefinition
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.def.WeaponDefinition
import io.luna.game.model.item.Equipment.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.Weapon
import java.util.*

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
        EquipmentDefinition.ALL.forEach {
            map.put(it.index, BotGearItem(it.index, it.id(), setOf(BotGearPurpose.MELEE, BotGearPurpose.PKING), 0))
        }
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
        map
    }

    /**
     * All known bot gear candidates, indexed by item id.
     *
     * This is a convenience lookup for systems that already know the item id and need the matching [BotGearItem] metadata
     * without scanning [ALL_GEAR]. It is derived from [ALL_GEAR], so it contains the same candidate items in a flattened
     * item-id lookup table.
     */
    val ALL_GEAR_IDS = lazyVal {
        val map = HashMap<Int, BotGearItem>()
        ALL_GEAR.value.values().forEach { map[it.id] = it }
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

                selected.sortByDescending { it.calculateScore() }
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
                if (equipment[AMMUNITION] == null) {
                    // Couldn't find matching ammo, fallback to melee, give bot some emergency ammo.
                    fill(WEAPON, setOf(BotGearPurpose.MELEE))
                    for(ammo in AmmoDefinition.ALL.values) {
                        if(ammo.weapons.contains(weapon)) {
                            bot.bank.add(Item(ammo.ammo.random(), 1000))
                            break
                        }
                    }
                }
            }
        }

        /**
         * Removes the selected shield if the weapon is two-handed.
         */
        private fun check2hWeapon() {
            val weapon = equipment[WEAPON]
            if (weapon != null && equipDef(weapon).isTwoHanded) {
                equipment[SHIELD] = null
            }
        }

        /**
         * Builds a locator for the current desired equipment layout.
         *
         * @return A locator that can locate and equip the selected gear.
         */
        fun buildLocator(): BotGearLocator {
            fillAmmo()
            check2hWeapon()
            return BotGearLocator(bot, equipment)
        }
    }

    /**
     * Finds the best owned equipment matching the supplied purposes.
     *
     * This first gives intelligent bots a chance to prefer coordinated gear sets. A set is considered usable when it
     * satisfies every requested purpose and the bot owns enough valid pieces to wear at least half of the set. The best
     * usable set is selected by priority, with a bonus when the full set is available, and every valid piece from that set
     * is placed into the equipment layout.
     *
     * If a complete set is selected, the layout is returned immediately. Otherwise, any missing slots are filled by the
     * generic purpose-based selector. The fallback selector scans every known gear candidate, keeps only valid items that
     * satisfy all requested purposes, groups them by equipment slot, and selects the highest-scoring item for each empty
     * slot.
     *
     * The returned builder can still be refined before equipping. For example, a script can replace a weapon, fill missing
     * slots with a secondary purpose, or request a specific utility item before calling [BotGearSelectorFill.buildLocator].
     *
     * @param bot The bot selecting gear.
     * @param purposes The purposes each selected gear item must satisfy.
     * @param excluding A predicate used to reject specific gear candidates.
     * @return A mutable fill builder containing the selected equipment layout.
     * @throws IllegalArgumentException If [purposes] is empty.
     */
    fun find(
        bot: Bot,
        purposes: Set<BotGearPurpose>,
        excluding: (BotGearItem) -> Boolean = { false }
    ): BotGearSelectorFill {
        require(purposes.isNotEmpty()) { "Must have at least one purpose." }

        // More intelligent bots tend to use gear sets.
        val equipment = arrayOfNulls<Int>(14)
        if (bot.personality.isIntelligent || rand(bot.personality.intelligence)) {
            // Determine which gear sets we can use. Must have at least size / 2 pieces of the set.
            val gearSelection = EnumMap<BotGearSet, Int>(BotGearSet::class.java)
            for (set in BotGearSet.entries) {
                if (!set.purposes.containsAll(purposes)) {
                    continue
                }
                var count = 0
                for (id in set.equipment) {
                    val item = ALL_GEAR_IDS.value[id]!!
                    if (!excluding(item) && valid(bot, item.index, item.id)) {
                        count++
                    }
                }
                val size = set.equipment.size
                if (count >= size / 2) {
                    gearSelection[set] = count
                }
            }

            // If we found equipable gearsets, find the best one and set all possible pieces.
            if (gearSelection.isNotEmpty()) {
                // Best one = highest priority + amount of pieces of the set we have.
                val selected = gearSelection.entries.maxBy {
                    var score = it.key.priority() + it.value // Default score based on priority and owned pieces.
                    if (it.value == it.key.equipment.size) {
                        // We have the entire set, give a bonus to the score.
                        score += it.value * 2
                    }
                    score
                }

                // Set all possible pieces.
                for (id in selected.key.equipment) {
                    val item = ALL_GEAR_IDS.value[id]!!
                    if (excluding(item) || !valid(bot, item.index, item.id)) {
                        continue
                    }
                    equipment[item.index] = item.id
                }

                // Dumb social bots prefer full sets, even when inefficient.
                if (selected.key.equipment.size == selected.value && bot.personality.isDumb && bot.personality.isSocial) {
                    return BotGearSelectorFill(bot, equipment)
                }
            }
        }

        // Default selection mode, determine all valid equipable items.
        val selection = ArrayListMultimap.create<Int, BotGearItem>()
        for (entry in ALL_GEAR.value.entries()) {
            val item = entry.value
            if (!valid(bot, entry.key, item.id) || !item.purposes.containsAll(purposes) || excluding(item)) {
                continue
            }
            selection.put(entry.key, item)
        }

        // Set the best item for each missing slot.
        for (index in equipment.indices) {
            val items = selection[index]
            val equipmentId = equipment[index]
            items.sortByDescending { it.calculateScore() }
            val selectionItem = items.firstOrNull()
            if (equipmentId == null) {
                equipment[index] = selectionItem?.id
            } else if(!bot.personality.isDumb) {
                // Bots that aren't stupid will use the most efficient equipment possible.
                val equipmentItem = ALL_GEAR_IDS.value[equipmentId]
                val selectionScore = selectionItem?.calculateScore() ?: 0
                if(equipmentItem != null && selectionScore > equipmentItem.calculateScore()) {
                    equipment[index] = selectionItem?.id
                }
            }
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

    fun computeWantedEquipment(bot: Bot): Set<Int> {
        val wanted = HashSet<Int>()
        /* for() {
          // todo adds wanted equipment for level if bot has none
         }*

         */
        return emptySet()
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