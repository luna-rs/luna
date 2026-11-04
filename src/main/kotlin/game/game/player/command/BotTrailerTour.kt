package game.player.command

import api.bot.isRealBot
import api.bot.zone.SubZone
import game.skill.magic.teleportSpells.TeleportAction
import io.luna.game.action.ActionType
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.CommandEvent
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.event.impl.InteractableEvent
import io.luna.game.event.impl.WalkingEvent
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.net.msg.out.CameraTargetMessageWriter
import io.luna.net.msg.out.ResetCameraMessageWriter
import org.apache.logging.log4j.LogManager
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Tours the occupied bot subzones through a player-owned spectator camera without directing any bot activity.
 * Sessions and camera continuations belong to the game thread and retain the player's ordinary save position.
 *
 * @author lare96
 */
object BotTrailerTour {
    private val logger = LogManager.getLogger(BotTrailerTour::class.java)
    private val sessions = IdentityHashMap<Player, Tour>()
    private val orbit = arrayOf(-5 to -3, -3 to -5, 3 to -5, 5 to -3,
        5 to 3, 3 to 5, -3 to 5, -5 to 3)

    /**
     * Starts one finite tour of the currently occupied subzones in their declaration order.
     */
    fun start(plr: Player, seconds: Int = 12) {
        if (sessions.containsKey(plr)) {
            plr.sendMessage("Your bot tour is already running. Use ::bottour next or ::bottour stop.")
            return
        }
        if (seconds !in 6..60) {
            plr.sendMessage("Choose between 6 and 60 seconds per bot subzone.")
            return
        }
        if (plr.state != EntityState.ACTIVE || !plr.isAlive || plr.client.isPendingLogout || plr.isLocked ||
            plr.walking.isLocked || plr.dynamicMap != null || plr.combat.inCombat() || plr.combat.target != null ||
            plr.controllers.primary.javaClass != PlayerController::class.java ||
            (plr is Bot && plr.isAutomationActive)) {
            plr.sendMessage("Finish your current activity or combat before starting a bot tour.")
            return
        }
        val bots = plr.world.players.mapNotNull {
            if (it.isRealBot() && it.isAlive && !it.client.isPendingLogout && it.dynamicMap == null) it else null
        }
        val zones = SubZone.entries.filter { zone -> bots.any { zone in it.subZones && zone.area.contains(it.position) } }
        if (zones.isEmpty()) {
            plr.sendMessage("There are no occupied bot subzones to film right now.")
            return
        }
        val tour = Tour(plr, zones, ceil(seconds / 0.6).toInt())
        sessions[plr] = tour
        try {
            plr.navigator.cancel()
            plr.actions.interruptAll()
            plr.walking.clear()
            plr.interact(null)
            plr.overlays.closeWindows()
            plr.controllers.register(tour.controller)
            plr.isModelHidden = true
            plr.sendMessage("Bot tour: ${zones.size} occupied subzones, ${seconds}s each. Use ::bottour next or stop.")
            tour.advance()
        } catch (failure: Exception) {
            tour.finish(false)
            logger.error("Could not start bot trailer tour for {}.", plr, failure)
            plr.sendMessage("The bot tour could not start. Your normal camera and position have been restored.")
        }
    }

    /**
     * Ends only this player's current tour and returns them to their original position.
     */
    fun stop(plr: Player): Boolean {
        val tour = sessions[plr] ?: return false
        tour.finish(true)
        return true
    }

    /**
     * Skips the current shot without changing the original return position.
     */
    fun next(plr: Player): Boolean {
        val tour = sessions[plr] ?: return false
        try {
            tour.requestNext()
        } catch (failure: Exception) {
            tour.finish(false)
            logger.error("Could not advance bot trailer tour for {}.", plr, failure)
        }
        return true
    }

    /**
     * Accepts scene readiness only for the active session's current loaded destination.
     */
    fun regionLoaded(plr: Player) {
        val tour = sessions[plr] ?: return
        if (tour.waiting && plr.position == tour.anchor && plr.lastRegion != null && !plr.needsRegionUpdate()) {
            tour.sceneReady = true
        }
    }

    /**
     * Releases any remaining session when logout completes; the controller also restores before saving.
     */
    fun logout(plr: Player) {
        sessions[plr]?.finish(false)
    }

    /**
     * Reports session ownership without exposing mutable tour state.
     */
    fun isActive(plr: Player): Boolean = sessions.containsKey(plr)

    /**
     * Owns one player's finite stop list, scene wait, orbit and idempotent return operation.
     *
     * @author lare96
     */
    private class Tour(val plr: Player, private val zones: List<SubZone>, private val shotTicks: Int) {
        val origin: Position = plr.position
        private val originalHidden = plr.isModelHidden
        val controller = TourController(this)
        private var closed = false
        private var index = -1
        private var ticks = 0
        private var waypoint = -1
        var anchor: Position = origin
            private set
        var waiting = true
            private set
        var sceneReady = false
        private var expectsRegion = false
        private var skipRequested = false

        /**
         * Finds a live local cluster rather than aiming at the possibly distant centre of a large subzone.
         */
        private fun residents(zone: SubZone): List<Bot> = plr.world.players.mapNotNull {
            if (it.isRealBot() && it.isAlive && !it.client.isPendingLogout && it.dynamicMap == null &&
                zone.area.contains(it.position)) it else null
        }

        /**
         * Lets an in-flight scene finish before skipping, so its unnumbered acknowledgement cannot ready a new stop.
         */
        fun requestNext() {
            if (closed) return
            if (waiting) skipRequested = true else advance()
        }

        /**
         * Revalidates each snapshotted stop, skipping zones whose bots have already left.
         */
        fun advance() {
            if (closed || sessions[plr] !== this) return
            while (++index < zones.size) {
                val bots = residents(zones[index])
                val focus = bots.take(64).maxByOrNull { candidate ->
                    bots.count { it.position.isWithinDistance(candidate.position, 8) }
                } ?: continue
                anchor = focus.position
                ticks = 0
                waypoint = -1
                waiting = true
                sceneReady = false
                skipRequested = false
                plr.queue(ResetCameraMessageWriter())
                plr.navigator.cancel()
                plr.move(anchor)
                expectsRegion = plr.lastRegion == null || plr.needsRegionUpdate()
                plr.sendMessage("Filming ${index + 1}/${zones.size}: ${zones[index].name.lowercase().replace('_', ' ')}.")
                return
            }
            finish(true)
        }

        /**
         * Runs at the ordinary game tick, retaining native client easing between the camera waypoints.
         */
        fun process() {
            if (closed) return
            try {
                if (sessions[plr] !== this || plr.controllers.primary !== controller ||
                    plr.state != EntityState.ACTIVE || !plr.isAlive || plr.client.isPendingLogout ||
                    plr.position != anchor || plr.isLocked || plr.dynamicMap != null) {
                    finish(false)
                    return
                }
                ++ticks
                if (waiting) {
                    if (ticks >= 25) {
                        finish(false)
                        plr.sendMessage("The game area took too long to load. The tour stopped and returned you to your starting position.")
                    } else if (ticks >= 2 && (!expectsRegion || sceneReady)) {
                        if (skipRequested) {
                            advance()
                        } else {
                            waiting = false
                            ticks = 0
                            camera(0, true)
                        }
                    }
                    return
                }
                val nearby = residents(zones[index]).filter { it.position.isWithinDistance(anchor, 12) }
                if (nearby.isEmpty() || ticks >= shotTicks) {
                    advance()
                    return
                }
                val nextWaypoint = (ticks * orbit.size / shotTicks).coerceAtMost(orbit.lastIndex)
                if (nextWaypoint != waypoint && !camera(nextWaypoint, false)) return
                val focus = nearby.minByOrNull { abs(it.x - anchor.x) + abs(it.y - anchor.y) } ?: return
                if (inScene(focus.position)) {
                    plr.queue(CameraTargetMessageWriter(focus.position, 90, 2, 8, true))
                }
            } catch (failure: Exception) {
                finish(false)
                logger.error("Bot trailer tour failed for {}.", plr, failure)
                plr.sendMessage("The bot tour stopped. Your normal camera and position have been restored.")
            }
        }

        /**
         * Keeps every camera tile on the player's plane and safely within native terrain-array bounds.
         */
        private fun inScene(position: Position): Boolean {
            val scene = plr.lastRegion ?: return false
            return position.z == plr.z && position.getLocalX(scene) in 2..101 && position.getLocalY(scene) in 2..101
        }

        /**
         * Snaps only the opening view; following orbit segments use the client's fixed and proportional easing.
         */
        private fun camera(next: Int, snap: Boolean): Boolean {
            val (dx, dy) = orbit[next]
            val position = anchor.translate(dx, dy)
            if (!inScene(position) || !inScene(anchor)) {
                advance()
                return false
            }
            waypoint = next
            plr.queue(CameraTargetMessageWriter(position, 420, 4, if (snap) 100 else 8, false))
            plr.queue(CameraTargetMessageWriter(anchor, 90, 2, if (snap) 100 else 8, true))
            return true
        }

        /**
         * Restores the origin while still a spectator, then releases only this session's controller.
         */
        fun finish(announce: Boolean, detach: Boolean = true) {
            if (closed) return
            closed = true
            if (sessions[plr] === this) sessions.remove(plr)
            try {
                plr.navigator.cancel()
                plr.walking.clear()
                plr.move(origin)
            } catch (failure: Exception) {
                logger.error("Could not return {} from their bot trailer tour.", plr, failure)
            } finally {
                try {
                    plr.isModelHidden = originalHidden
                    plr.queue(ResetCameraMessageWriter())
                } finally {
                    if (detach && plr.controllers.primary === controller) plr.controllers.unregister()
                }
            }
            if (announce && plr.state == EntityState.ACTIVE && !plr.client.isPendingLogout) {
                plr.sendMessage("Bot tour finished. You have returned to your starting position.")
            }
        }
    }

    /**
     * Allows cancellation, logout and scene acknowledgements while isolating the camera from gameplay input.
     *
     * @author lare96
     */
    private class TourController(private val tour: Tour) : PlayerController(tour.plr) {
        override fun isSpectator(): Boolean = true
        override fun getPersistencePosition(): Position = tour.origin
        override fun beforeLogout() { tour.finish(false) }
        override fun logout(): Boolean { tour.finish(false); return true }
        override fun unregister() { tour.finish(false, false) }
        override fun process() { tour.process() }
        override fun teleport(action: TeleportAction): Boolean = false
        override fun combat(victim: Mob): Boolean = false

        override fun event(event: ControllableEvent): Boolean {
            if (event is CommandEvent) {
                if (event.name !in setOf("bottour", "invis", "visible")) tour.finish(false)
                return true
            }
            if (event is ButtonClickEvent && event.id == 2458) {
                tour.finish(false)
                return true
            }
            if (event is WalkingEvent || event is InteractableEvent) tour.finish(true)
            return false
        }
    }
}
