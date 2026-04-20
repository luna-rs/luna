package game.item.degradable

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import game.item.degradable.DegradableEquipmentHandler.degrade
import io.luna.game.model.item.DynamicItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Central lookup and state handler for degradable equipment.
 *
 * This object is responsible for loading degradable equipment, exposing fast lookups by type and item id,
 * persisting charge-related state, and advancing equipped items through their degradation chains.
 *
 * The handler does not decide *when* an item should degrade. Instead, callers provide the filtering and degradation
 * rules through lambdas passed to [degrade].
 *
 * @author lare96
 */
object DegradableEquipmentHandler {

    /**
     * Stores the ids of items that are subject to opening [DegradableDropWarningDialogue] when dropped.
     */
    private val dropRestricted: HashSet<Int> = HashSet()

    /**
     * Maps an item id to both its owning degradable set and its specific degradable chain entry.
     *
     * This allows constant-time lookup of degradation metadata for a concrete item id.
     */
    private val idMap: HashMap<Int, Pair<DegradableItemSet, DegradableItem>> = HashMap()

    /**
     * The total amount of reflected damage a Ring of Recoil can absorb before breaking.
     */
    const val RING_OF_RECOIL_CHARGES = 40

    /**
     * The health percentage at or below which a Ring of Life should activate.
     */
    const val RING_OF_LIFE_HEALTH_PERCENT = 10

    /**
     * The starting charge count assigned to Crystal items managed by this handler.
     */
    const val CRYSTAL_ITEMS_CHARGES = 2500

    /**
     * Tracks whether the player's Ring of Life has already activated for the current situation.
     */
    var Player.ringOfLifeActive by Attr.boolean()

    /**
     * The player's remaining Ring of Recoil capacity.
     */
    var Player.ringOfRecoilCharges by Attr.int { RING_OF_RECOIL_CHARGES }
        .persist("ring_of_recoil_charges")

    /**
     * The remaining charges stored on degradable equipment.
     */
    var DynamicItem.charges by Attr.int().persist("charges")

    /**
     * Loads degradable equipment into the handler's internal lookup tables.
     *
     * Each [DegradableItem] is indexed by item id in [idMap]. Items that are not the starting stage of their
     * degradation chain, identified by a [DegradableItem.prevId] other than `-1`, are also added to
     * [dropRestricted].
     *
     * Item ids must be unique across all loaded degradable sets. If the same id appears more than once, loading
     * fails with an [IllegalStateException] to prevent ambiguous degradation lookups.
     *
     * @param all The degradable item sets to register.
     * @throws IllegalStateException If more than one degradable entry uses the same item id.
     */
    fun load(all: Array<DegradableItemSet>) {
        for (set in all) {
            for (degradable in set.items) {
                if (idMap.containsKey(degradable.id)) {
                    throw IllegalStateException("IDs in degradable item sets must be unique! (item=${degradable.id})")
                }
                if (degradable.prevId != -1) {
                    dropRestricted += degradable.id
                }
                idMap[degradable.id] = set to degradable
            }
        }
    }

    /**
     * Looks up degradable metadata for the supplied item id.
     *
     * @param id The item id to resolve.
     * @return A pair containing the owning [DegradableItemSet] and the matching [DegradableItem], or `null` if the
     * item id is not registered as degradable.
     */
    fun getDegradable(id: Int): Pair<DegradableItemSet, DegradableItem>? = idMap.getOrDefault(id, null)

    /**
     * Returns whether the supplied item id is drop restricted.
     *
     * @param id The item id to test.
     * @return `true` if the item id exists in [dropRestricted], or `false` otherwise.
     */
    fun isDropRestricted(id: Int) = dropRestricted.contains(id)

    /**
     * Applies degradation logic to the equipped item in the specified slot.
     *
     * The slot is ignored if it is empty or if the equipped item id is not present in the degradable lookup table.
     * For matching items, [filter] is used to decide whether that degradable type should be processed.
     *
     * If the equipped item is still a plain [Item], it is converted into a [DynamicItem] with [initialCharges].
     * If it is already a [DynamicItem], [degraded] is evaluated to determine whether the item should advance to its
     * next stage.
     *
     * When a stage transition occurs, the equipped item is replaced with the result of [nextItem]. The replacement
     * may be another degradable [DynamicItem], a plain [Item], or `null` if the item has no further stage and should
     * be removed. After the transition, [degradedMsg] is invoked with the replacement item. If it returns a non-null
     * message, that message is sent to the player.
     *
     * @param plr The player whose equipped item should be processed.
     * @param index The equipment slot index to inspect.
     * @param initialCharges The charge count to assign when creating a new degradable dynamic item.
     * @param filter Returns `true` if the matched degradable type should be processed.
     * @param degraded Returns `true` if the current dynamic item should advance to its next degradation stage.
     * @param degradedMsg Produces the message to send after a successful stage transition. The argument is the
     * replacement item, or `null` if the item was removed.
     * @return `true` if the equipped item changed stage or was removed, or `false` otherwise.
     */
    fun degrade(
        plr: Player,
        index: Int,
        initialCharges: Int,
        filter: (DegradableItemType) -> Boolean,
        degraded: (DynamicItem) -> Boolean,
        degradedMsg: (Item?) -> String?
    ): Boolean {
        val item = plr.equipment[index] ?: return false
        val (set, degradable) = idMap.getOrDefault(item.id, null) ?: return false
        if (filter(set.type)) {
            if (item !is DynamicItem) {
                plr.equipment[index] = buildDynamicItem(item.id, initialCharges)
            } else if (degraded(item)) {
                val nextItem = nextItem(degradable, initialCharges)
                plr.equipment[index] = nextItem
                val message = degradedMsg(nextItem)
                if (message != null) {
                    plr.sendMessage(message)
                }
                return true
            }
        }
        return false
    }

    /**
     * Creates the item that should replace the current degradable stage.
     *
     * A `nextId` of `-1` means the current stage has no further form and should be removed. Otherwise, the next id is
     * checked against the loaded degradable lookup table. If the next id is also degradable, a [DynamicItem] is
     * created and initialized with [initialCharges]. If it is not degradable, a plain [Item] is created instead.
     *
     * @param degradable The degradable entry representing the current item stage.
     * @param initialCharges The charge count to assign if the next stage is also degradable.
     * @return The replacement item for the next stage, or `null` if the current item should disappear.
     */
    private fun nextItem(degradable: DegradableItem, initialCharges: Int): Item? {
        if (degradable.nextId == -1) {
            return null
        }
        return if (idMap.containsKey(degradable.nextId)) {
            buildDynamicItem(degradable.nextId, initialCharges)
        } else {
            Item(degradable.nextId)
        }
    }

    /**
     * Creates a degradable [DynamicItem] with its initial charge state applied.
     *
     * @param id The item id to create.
     * @param initialCharges The starting charge count to store on the new item.
     * @return A new [DynamicItem] with [charges] initialized to [initialCharges].
     */
    private fun buildDynamicItem(id: Int, initialCharges: Int): DynamicItem {
        val item = DynamicItem(id)
        item.charges = initialCharges
        return item
    }
}