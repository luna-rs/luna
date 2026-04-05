package game.item.degradable

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import io.luna.game.model.def.DegradableItemDefinition
import io.luna.game.model.item.DynamicItem
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Utility methods and persistent attributes for degradable equipment handling.
 *
 * This object is responsible for:
 * - Tracking per-item degradation state such as charges and active combat ticks
 * - Processing Barrows equipment degradation over time in combat
 * - Processing Crystal bow degradation on use
 * - Replacing items with their next degradable form when a threshold is reached
 *
 * @author lare96
 */
object DegradableItems {

    /**
     * The default amount of damage that can be reflected before a Ring of Recoil shatters.
     */
    const val RING_OF_RECOIL_CHARGES = 40

    /**
     * The starting charge count assigned to a Barrows item when it enters a new degradable state.
     */
    private const val BARROWS_CHARGES = 250

    /**
     * The number of active combat ticks required to consume one Barrows charge.
     */
    private const val BARROWS_TICKS_PER_CHARGE = 90

    /**
     * The health percentage threshold at which a Ring of Life should trigger.
     */
    const val RING_OF_LIFE_HEALTH_PERCENT = 10

    /**
     * The starting charge count assigned to Crystal items handled by this module.
     */
    const val CRYSTAL_ITEMS_CHARGES = 2500

    /**
     * If the player's Ring of Life has just been activated.
     */
    var Player.ringOfLifeActive by Attr.boolean()

    /**
     * The player's remaining Ring of Recoil charges.
     *
     * This value persists across sessions and defaults to [RING_OF_RECOIL_CHARGES].
     */
    var Player.ringOfRecoilCharges by Attr.int { RING_OF_RECOIL_CHARGES }
        .persist("ring_of_recoil_charges")

    /**
     * The number of active combat ticks accumulated on a degradable dynamic item.
     *
     * This is primarily used for time-based degradation such as Barrows equipment, where charges are consumed only
     * after enough combat activity has elapsed.
     */
    private var DynamicItem.activeCombatTicks by Attr.int()
        .persist("active_combat_ticks")

    /**
     * The remaining charges on a degradable dynamic item.
     *
     * This value persists with the item and is decremented according to the degradation rules for that item type.
     */
    var DynamicItem.charges by Attr.int()
        .persist("charges")

    /**
     * Processes Barrows equipment degradation for all equipped items on the player.
     *
     * Each equipped Barrows item is checked once per call. When a Barrows item is active in combat, its
     * `activeCombatTicks` value is incremented. Once enough ticks have elapsed, one charge is consumed. If the item
     * runs out of charges, it is replaced with its next degradable form, or removed entirely if no next form exists.
     *
     * Only one degradation message is sent per invocation even if multiple items degrade during the same pass.
     *
     * @param plr The player whose equipped Barrows items should be processed.
     */
    fun handleBarrowsEquipment(plr: Player) {
        var sent = false
        for ((index, item) in plr.equipment.withIndex()) {
            if (item != null) {
                degrade(plr,
                        index,
                        BARROWS_CHARGES,
                        DegradableItemType.BARROWS,
                        {
                            if (++it.activeCombatTicks >= BARROWS_TICKS_PER_CHARGE) {
                                it.activeCombatTicks = 0
                                if (--it.charges <= 0) {
                                    return@degrade true
                                }
                            }
                            return@degrade false
                        },
                        {
                            if (!sent) {
                                sent = true
                                return@degrade if (it == null)
                                    "Your barrows equipment has degraded completely." else
                                    "Your barrows equipment has degraded."
                            } else {
                                return@degrade null
                            }
                        })
            }
        }
    }

    /**
     * Processes Crystal bow degradation for the player's equipped weapon.
     *
     * If the equipped weapon is a degradable Crystal bow, one charge is consumed. When all charges are exhausted, the
     * weapon is replaced with its next degradable form. If the definition has no next form, the bow is removed and
     * a crystal seed is given instead.
     *
     * The returned value reflects whether the player still has a weapon equipped after degradation has been processed.
     *
     * @param plr The player whose Crystal bow state should be processed.
     * @return `true` if the player still has an equipped weapon after handling; `false` otherwise.
     */
    fun handleCrystalBow(plr: Player): Boolean {
        degrade(
            plr,
            Equipment.WEAPON,
            CRYSTAL_ITEMS_CHARGES,
            DegradableItemType.CRYSTAL_BOW,
            { --it.charges <= 0 },
            {
                if (it == null) {
                    plr.giveItem(Item(4207)) // TODO Does seed appear in weapon slot?
                    return@degrade "Your crystal bow reverts into a seed."
                } else {
                    return@degrade "Your crystal bow has degraded a little."
                }
            }
        )
        return plr.equipment.weapon != null
    }

    /**
     * Applies a single degradation step to the item equipped in the given slot, if that item matches the requested
     * degradable type.
     *
     * If the equipped item is not present, has no degradable definition, or does not match the supplied [type],
     * nothing happens and `false` is returned.
     *
     * If the item is not already a [DynamicItem], or if [degraded] reports that the current dynamic item should
     * degrade, the item is replaced with its next form as defined by [DegradableItemDefinition]. A new dynamic item
     * created by this transition receives [initialCharges]. If there is no next form, the equipped item is removed.
     *
     * After the transition, [degradedMsg] is invoked with the replacement item, or `null` if the item degraded
     * completely. If a non-null message is returned, it is sent to the player.
     *
     * @param plr The player whose equipment should be updated.
     * @param index The equipment slot index to inspect.
     * @param initialCharges The charge count assigned to the next item form when a degradation step occurs.
     * @param type The degradable item category that must match before processing.
     * @param degraded A predicate that decides whether the current dynamic item should advance to its next degradable
     * form.
     * @param degradedMsg Supplies the message to send after degradation. The argument is the replacement item, or
     * `null` if the item fully degraded and disappeared.
     * @return `true` if the equipment slot was changed by degradation; `false` otherwise.
     */
    fun degrade(
        plr: Player,
        index: Int,
        initialCharges: Int,
        type: DegradableItemType,
        degraded: (DynamicItem) -> Boolean,
        degradedMsg: (DynamicItem?) -> String?
    ): Boolean {
        val item = plr.equipment[index] ?: return false
        val def = DegradableItemDefinition.ALL[item.id].orElse(null)
        if (def?.type == type) {
            if (item !is DynamicItem || degraded(item)) {
                val nextItem = nextItem(def, initialCharges)
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
     * Creates the next degradable item form for the supplied definition.
     *
     * If the definition has no further stage, `null` is returned to indicate that the item should be removed instead
     * of replaced. Otherwise, a new [DynamicItem] is created from `def.nextId` and initialized with [initialCharges].
     *
     * @param def The degradable item definition describing the current stage.
     * @param initialCharges The charge count to assign to the newly created next stage item.
     * @return The next degradable item form, or `null` if the current item has no further stage.
     */
    private fun nextItem(def: DegradableItemDefinition, initialCharges: Int): DynamicItem? {
        if (def.nextId == -1) {
            return null
        }
        val newItem = DynamicItem(def.nextId)
        newItem.charges = initialCharges
        return newItem
    }
}