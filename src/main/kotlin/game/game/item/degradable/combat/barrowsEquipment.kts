package game.item.degradable.combat

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.combat.player.PlayerCombatHandler.playerCombat
import game.item.degradable.DegradableEquipmentHandler.charges
import game.item.degradable.DegradableEquipmentHandler.degrade
import io.luna.game.model.item.DynamicItem

/**
 * The charge count assigned to a Barrows item when it first becomes tracked or advances to its next degradable stage.
 */
val BARROWS_CHARGES = 1

/**
 * The amount of combat-active time, measured in ticks, required to consume one Barrows charge.
 */
val BARROWS_TICKS_PER_CHARGE = 90

/**
 * The number of combat-active ticks accumulated on a degradable Barrows item. One charge is consumed each time this
 * counter reaches [BARROWS_TICKS_PER_CHARGE].
 */
var DynamicItem.activeCombatTicks by Attr.int().persist("active_combat_ticks")

/**
 * Advances the active combat tick counter for a Barrows item and consumes a charge when the threshold is reached.
 *
 * Each call adds one active combat tick. When the accumulated tick count reaches [BARROWS_TICKS_PER_CHARGE], the
 * counter is reset and one charge is removed from the item.
 *
 * @param item The Barrows item whose combat-active degradation state should be updated.
 * @return `true` if the consumed charge reduced the item to zero charges and it should degrade to its next stage, or
 * `false` if no stage transition should occur yet.
 */
fun reduceCharges(item: DynamicItem): Boolean {
    if (++item.activeCombatTicks >= BARROWS_TICKS_PER_CHARGE) {
        item.activeCombatTicks = 0
        if (--item.charges <= 0) {
            return true
        }
    }
    return false
}

playerCombat {
    var sent = false
    for ((index, item) in equipment.withIndex()) {
        if (item != null) {
            // Degrade barrows type equipment on any index.
            degrade(this, index,
                    BARROWS_CHARGES,
                    { it.isBarrows() },
                    { reduceCharges(it) },
                    {
                        if (!sent) {
                            sent = true
                            if (it == null) "Your barrows equipment has degraded completely."
                            else "Your barrows equipment has degraded."
                        } else {
                            null
                        }
                    })
        }
    }
}