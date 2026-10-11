package game.content.gnomeGliders

import api.predef.*
import api.predef.ext.*
import game.skill.magic.Magic.findLandingTile
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.Varp

/**
 * Flies players between Gnome Air glider sites.
 *
 * @author TheLining
 */
object GnomeGliders {

    /**
     * The glider map, where players pick where to fly.
     */
    const val MAP_INTERFACE = 802

    /**
     * The varp that picks which flight the glider map shows.
     */
    const val FLIGHT_VARP = 153

    /**
     * The glider map, opened by the pilot at [from].
     */
    class GliderMap(val from: GliderSite) : StandardInterface(MAP_INTERFACE)

    /**
     * Opens the glider map for [plr], who is at [from].
     */
    fun openMap(plr: Player, from: GliderSite) {
        plr.overlays.open(GliderMap(from))
    }

    /**
     * Flies [plr] from [from] to [to], if Gnome Air flies that way.
     */
    fun select(plr: Player, from: GliderSite, to: GliderSite) {
        if (from == to) {
            plr.sendMessage("You're already there.")
            return
        }
        val flight = when {
            from == GliderSite.TA_QUIR_PRIW -> to.outbound
            to == GliderSite.TA_QUIR_PRIW -> from.inbound
            else -> null
        }
        if (flight == null) {
            plr.sendMessage("You can't go there at the moment.")
            return
        }
        // TODO One Small Favour: only players who have completed it can fly to Lemantolly Undri.
        fly(plr, to, flight)
    }

    /**
     * Shows [flight] on the glider map and lands [plr] at [to].
     */
    private fun fly(plr: Player, to: GliderSite, flight: Int) {
        plr.lock()
        plr.sendVarp(Varp(FLIGHT_VARP, flight))
        world.scheduleOnce(1) {
            if (plr.state == EntityState.ACTIVE) {
                plr.moveKeepingWindows(findLandingTile(to.landing, 1))
            }
        }
        world.scheduleOnce(3) {
            if (plr.state == EntityState.ACTIVE) {
                plr.sendVarp(Varp(FLIGHT_VARP, -1))
                plr.overlays.closeWindows()
                plr.unlock()
            }
        }
    }
}
