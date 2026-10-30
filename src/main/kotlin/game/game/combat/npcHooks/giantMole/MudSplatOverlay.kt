package game.combat.npcHooks.giantMole

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface

/**
 * The mud the Giant Mole throws onto a player's screen as it burrows. It opens as the main interface so the client
 * draws it over the darkness.
 *
 * @author TheLining
 */
class MudSplatOverlay : StandardInterface(18176) {

    companion object {

        /**
         * Covers [player]'s screen in mud for [GiantMole.MUD_SPLAT_TICKS] ticks.
         */
        fun show(player: Player) {
            val overlay = MudSplatOverlay()
            player.overlays.open(overlay)
            world.scheduleOnce(GiantMole.MUD_SPLAT_TICKS) {
                if (player.overlays.getOverlay(MudSplatOverlay::class.java) === overlay) {
                    player.overlays.closeWindows(false)
                }
            }
        }
    }
}
