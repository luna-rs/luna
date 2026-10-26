package game.content.rangingGuild

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.ExactMovement
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import java.util.concurrent.CompletableFuture

/**
 * Approaches either side of the diagonal guild entrance and enforces level 40 Ranged only on entry.
 *
 * @author lare96
 */
object RangingGuildGate {
    val POSITION = Position(2658, 3438)
    val OUTSIDE = Position(2657, 3439)
    val INSIDE = Position(2659, 3437)

    fun enter(p: Player, door: GameObject): Boolean {
        if (!ArcheryCompetition.ready(p) || door.id != 2514 || door.position != POSITION ||
            door.state != EntityState.ACTIVE || !p.position.isWithinDistance(POSITION, 3)) return false
        p.overlays.closeWindows()
        p.submitAction(Approach(p, door))
        return true
    }

    fun logout(p: Player) {
        p.actions.first(Approach::class.java)?.interrupt()
        p.actions.first(Crossing::class.java)?.interrupt()
    }

    /**
     * Ordinary navigation reaches the correct corner before the locked passage starts.
     *
     * @author lare96
     */
    internal class Approach(p: Player, private val door: GameObject) : Action<Player>(p, ActionType.WEAK) {
        private val entering = p.x <= POSITION.x && p.y >= POSITION.y
        private val start = if (entering) OUTSIDE else INSIDE
        private var walking: CompletableFuture<NavigationResult>? = null
        private var ticks = 0

        override fun run(): Boolean {
            if (++ticks > 30 || !ArcheryCompetition.ready(mob) || door.state != EntityState.ACTIVE ||
                !mob.position.isWithinDistance(POSITION, 4)) return true
            if (walking?.isDone == false) return false
            if (mob.position != start) {
                if (walking != null) return true
                walking = mob.navigator.navigate(start, false)
                return false
            }
            if (entering && mob.ranged.level < 40) {
                mob.sendMessage("You need a Ranged level of 40 to enter the Ranging Guild.")
            } else mob.submitAction(Crossing(mob, if (entering) INSIDE else OUTSIDE))
            return true
        }
    }

    /**
     * Moves through the diagonal doorway without opening a shared level-restricted route for other players.
     *
     * @author lare96
     */
    internal class Crossing(p: Player, private val destination: Position) : LockedAction(p) {
        private val origin = p.position
        private var ticks = 0

        override fun onLock() {
            mob.walking.clear()
            mob.playSound(Sound.DOOR_OPEN)
            mob.exactMove(ExactMovement(mob.lastRegion, origin, destination, 0, 40, Direction.between(origin, destination)))
        }

        override fun run(): Boolean {
            if (mob.state != EntityState.ACTIVE || !mob.isAlive || mob.position != origin) return true
            if (++ticks < 2) return false
            mob.move(destination)
            return true
        }
    }
}
