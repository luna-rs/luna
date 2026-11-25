package game.item.degradable.jewellery

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import engine.bot.economy.BotConsumableDemand
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player

/**
 * The ring of forging. While worn, every iron ore smelted in a furnace becomes a bar. The ring melts after 140 bars.
 */
object RingOfForging {

    /**
     * The ring of forging item id.
     */
    const val ID = 2568

    /**
     * The amount of iron bars one ring smelts before it melts.
     */
    const val CHARGES = 140

    /**
     * The iron bars the player can still smelt before their ring melts. The count belongs to the player, so a
     * second ring carries on where the first one stopped.
     */
    var Player.ringOfForgingCharges: Int by Attr.int { CHARGES }.persist("ring_of_forging_charges")

    /**
     * Returns `true` if [plr] is wearing a ring of forging.
     */
    fun isWorn(plr: Player): Boolean = plr.equipment.ring?.id == ID

    /**
     * Uses one charge of the worn ring, and melts it when the last charge is gone.
     */
    fun useCharge(plr: Player) {
        if (--plr.ringOfForgingCharges > 0) {
            return
        }
        plr.equipment[Equipment.RING] = null
        plr.ringOfForgingCharges = CHARGES
        plr.sendMessage("Your Ring of Forging has melted.")
        BotConsumableDemand.depleted(plr, ID)
    }
}
