package game.skill.firemaking.lightSource

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.varp.Varbit

/**
 * The lamp oil still in the Rimmington chemist's house. Swamp tar goes in, and one lamp or lantern can then be
 * filled from it. The still holds one fill at a time, and each player has their own.
 *
 * @author TheLining
 */
object LampOilStill {

    /**
     * The still. It is drawn empty or full from [OIL_VARBIT].
     */
    const val STILL = 5908

    /**
     * The varbit that shows oil in the still.
     */
    const val OIL_VARBIT = 281

    /**
     * Swamp tar.
     */
    const val SWAMP_TAR = 1939

    /**
     * Each lamp or lantern that needs oil, and what it becomes when filled.
     */
    val FILLED = mapOf(
        4525 to 4522, // Oil lamp
        4535 to 4537, // Oil lantern
        4546 to 4548, // Bullseye lantern
        4700 to 4701  // Sapphire lantern
    )

    /**
     * Whether this player's still has oil in it.
     */
    var Player.lampOilInStill by Attr.boolean().persist("lamp_oil_in_still")

    /**
     * Registers the saved attribute before any player is loaded.
     */
    fun initialize() = Unit

    /**
     * Shows [plr] their still as empty or full.
     */
    fun show(plr: Player) {
        plr.sendVarbit(Varbit(OIL_VARBIT, if (plr.lampOilInStill) 1 else 0))
    }

    /**
     * Distils one swamp tar from [plr]'s inventory into lamp oil.
     */
    fun addTar(plr: Player) {
        if (plr.lampOilInStill) {
            plr.sendMessage("There is already lamp oil in the still.")
        } else if (plr.inventory.remove(Item(SWAMP_TAR))) {
            plr.lampOilInStill = true
            show(plr)
            plr.sendMessage("You pour the swamp tar into the still, and it is refined into lamp oil.")
        }
    }

    /**
     * Fills the empty lamp or lantern in slot [index] of [plr]'s inventory.
     */
    fun fill(plr: Player, index: Int) {
        val filled = FILLED[plr.inventory[index]?.id] ?: return
        if (!plr.lampOilInStill) {
            plr.sendMessage("There is no oil in the still.")
            return
        }
        plr.lampOilInStill = false
        show(plr)
        plr.inventory[index] = Item(filled)
        plr.sendMessage("You fill the ${itemName(filled).lowercase()} with oil.")
    }
}
