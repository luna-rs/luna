package game.content.templeTrekking

import api.predef.*
import api.predef.ext.*
import game.content.templeTrekking.TempleTrekking.RouteMap
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.WalkableInterface
import io.luna.game.model.mob.varp.Varbit
import io.luna.net.msg.out.WidgetAnimationMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * The trek on the route map, from Burgh de Rott or the last event to the next event or the temple. The pawns walk
 * each leg; when an event lies at the end of one, its "!" flashes and the screen fades into it.
 *
 * The player is held still the whole way. A world task drives the map instead of an action, because the action
 * queue closes every window while a strong action runs.
 *
 * @author TheLining
 */
class TrekJourney(private val plr: Player, private val trek: Trek) {

    companion object {

        /**
         * The fade overlay, shared with the canoes, and its fade-out and fade-in animations.
         */
        private const val FADE_OVERLAY = 11124
        private const val FADE_WIDGET = 11125
        private const val FADE_OUT = 1377
        private const val FADE_IN = 1378

        /**
         * How long the "!" flashes before the fade, and how long the fade takes.
         */
        private const val FLASH_TICKS = 4
        private const val FADE_TICKS = 2

        /**
         * Sets [trek] going on the map from the leg it's on.
         */
        fun start(plr: Player, trek: Trek) = TrekJourney(plr, trek).begin()

        /**
         * Fades the screen to black or back for [plr].
         */
        fun fade(plr: Player, sequence: Int) {
            plr.queue(WidgetAnimationMessageWriter(FADE_WIDGET, -1))
            val overlay = WalkableInterface(FADE_OVERLAY)
            plr.overlays.open(overlay)
            plr.queue(WidgetItemModelMessageWriter(FADE_WIDGET, 1000, 4032))
            plr.queue(WidgetAnimationMessageWriter(FADE_WIDGET, sequence))
            if (sequence == FADE_IN) {
                world.scheduleOnce(3) {
                    if (plr.overlays.getOverlay(WalkableInterface::class.java) === overlay) {
                        plr.overlays.closeWalkable()
                    }
                }
            }
        }
    }

    private enum class Stage { WALK, ARRIVE, FLASH, ENTER }

    private var stage = Stage.WALK
    private var wait = 1
    private var event: TrekEventType? = null

    private fun begin() {
        plr.walking.clear()
        plr.combat.target = null
        plr.lock()
        // The map already open from choosing the route stays open; reopening it would close it on the client.
        val open = plr.overlays.getOverlay(RouteMap::class.java)
        if (open != null) {
            open.travelling = true
        } else {
            plr.overlays.open(RouteMap(trek.villager).also { it.travelling = true })
        }
        plr.sendVarbit(Varbit(TempleTrekking.MARKER_VARBIT, 0))
        val previous = trek.route.legs.getOrNull(trek.leg - 1)
        plr.queue(WidgetAnimationMessageWriter(TempleTrekking.PAWNS, previous?.hold ?: TempleTrekking.PAWNS_AT_START))
        world.schedule(1) { task ->
            if (step()) {
                task.cancel()
                end()
            }
        }
    }

    /**
     * Runs a tick of the journey. Returns `true` once it's over.
     */
    private fun step(): Boolean {
        if (plr.state != EntityState.ACTIVE || !trek.active) return true
        if (wait > 0) {
            wait--
            return false
        }
        val leg = trek.route.legs[trek.leg]
        when (stage) {
            Stage.WALK -> {
                plr.queue(WidgetAnimationMessageWriter(TempleTrekking.PAWNS, leg.trip))
                wait = leg.ticks
                stage = Stage.ARRIVE
            }

            Stage.ARRIVE -> {
                if (leg.hold == -1) {
                    end()
                    TrekEvents.arrive(plr, trek)
                    return true
                }
                plr.queue(WidgetAnimationMessageWriter(TempleTrekking.PAWNS, leg.hold))
                event = trek.plan.remove(trek.leg)
                if (event != null) {
                    plr.sendVarbit(Varbit(TempleTrekking.MARKER_VARBIT, leg.marker))
                    wait = FLASH_TICKS
                    stage = Stage.FLASH
                } else {
                    trek.leg++
                    stage = Stage.WALK
                }
            }

            Stage.FLASH -> {
                fade(plr, FADE_OUT)
                wait = FADE_TICKS
                stage = Stage.ENTER
            }

            Stage.ENTER -> {
                end()
                plr.sendVarbit(Varbit(TempleTrekking.MARKER_VARBIT, 0))
                trek.leg++
                TrekEvents.enter(plr, trek, event!!)
                fade(plr, FADE_IN)
                return true
            }
        }
        return false
    }

    /**
     * Lets the player go and takes the map away.
     */
    private fun end() {
        if (plr.overlays.getOverlay(RouteMap::class.java) != null) plr.overlays.closeWindows()
        if (plr.isLocked) plr.unlock()
    }
}
