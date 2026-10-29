package game.obj.doors

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.ExactMovement
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import java.util.concurrent.CompletableFuture

/**
 * The large gate at the south entrance of the Tree Gnome Stronghold. It is one 5x2 object rather than a door, so it isn't
 * in the door files. Opening it swings both halves open for a few ticks while the player walks through the middle.
 *
 * @author TheLining
 */
object GnomeStrongholdGate {

    /**
     * The id of the closed gate.
     */
    const val GATE = 190

    /**
     * The id of the left half of the open gate.
     */
    private const val OPEN_LEFT = 191

    /**
     * The id of the right half of the open gate.
     */
    private const val OPEN_RIGHT = 192

    /**
     * The id of the invisible wall that fills the gateway while the gate is open, so only the player that opened it
     * goes through.
     */
    private const val BLOCKER = 83

    /**
     * The south-west corner of the closed gate.
     */
    val POSITION = Position(2459, 3383)

    /**
     * The tile in front of the gate, outside the Stronghold.
     */
    val SOUTH = Position(2461, 3382)

    /**
     * The tile behind the gate, inside the Stronghold.
     */
    val NORTH = Position(2461, 3385)

    /**
     * The ticks that the gate stays open for.
     */
    private const val OPEN_TICKS = 5

    /**
     * The most ticks that a player is given to reach the gate and for it to be free.
     */
    private const val MAX_TICKS = 30

    /**
     * If the gate is open for someone.
     */
    private var busy = false

    /**
     * Sends [plr] through [gate].
     */
    fun open(plr: Player, gate: GameObject) {
        // TODO Once The Grand Tree is added, the gate guard keeps players out, or in, at its quest stages.
        if (gate.id != GATE || gate.position != POSITION) {
            return
        }
        if (plr.actions.first(Approach::class.java) == null) {
            plr.submitAction(Approach(plr))
        }
    }

    /**
     * Swaps the closed gate for its open halves and puts it back after [OPEN_TICKS].
     *
     * @return `false` if the gate is busy or not standing.
     */
    private fun openGate(): Boolean {
        if (busy) {
            return false
        }
        val closed = world.objects.findAll(POSITION).filter { it.id == GATE }.findFirst().orElse(null) ?: return false
        if (!world.removeObject(closed)) {
            return false
        }
        val placed = listOf(
            world.addObject(OPEN_LEFT, POSITION, ObjectType.DEFAULT, ObjectDirection.EAST),
            world.addObject(OPEN_RIGHT, POSITION.translate(3, 0), ObjectType.DEFAULT, ObjectDirection.WEST),
            world.addObject(BLOCKER, POSITION.translate(2, 0), ObjectType.DEFAULT, ObjectDirection.SOUTH),
            world.addObject(BLOCKER, POSITION.translate(2, 1), ObjectType.DEFAULT, ObjectDirection.NORTH))
        busy = true
        world.scheduleOnce(OPEN_TICKS) {
            try {
                placed.forEach { world.removeObject(it) }
                world.addObject(closed.id, closed.position, closed.objectType, closed.direction)
            } finally {
                busy = false
            }
        }
        return true
    }

    /**
     * Walks to the tile in front of the gate on the player's side and waits for the gate to be free. Walking away cancels
     * it.
     *
     * @author TheLining
     */
    private class Approach(plr: Player) : Action<Player>(plr, ActionType.WEAK) {
        private val entering = plr.position.y < POSITION.y
        private val start = if (entering) SOUTH else NORTH
        private var walking: CompletableFuture<NavigationResult>? = null
        private var ticks = 0

        override fun run(): Boolean {
            if (++ticks > MAX_TICKS) {
                mob.sendMessage("You can't get through the gate right now.")
                return true
            }
            val pending = walking
            if (pending != null && !pending.isDone) {
                return false
            }
            if (mob.position != start) {
                if (pending != null) {
                    mob.sendMessage("You can't reach the gate from here.")
                    return true
                }
                walking = mob.navigator.navigate(start, false)
                return false
            }
            if (!openGate()) {
                return false
            }
            mob.submitAction(Crossing(mob, if (entering) NORTH else SOUTH))
            return true
        }
    }

    /**
     * Walks the player through the open gate, past the invisible walls.
     *
     * @author TheLining
     */
    private class Crossing(plr: Player, private val destination: Position) : LockedAction(plr) {
        private var ticks = 0

        override fun onLock() {
            mob.walking.clear()
            mob.exactMove(ExactMovement.to(mob, destination))
            mob.move(destination)
        }

        override fun run(): Boolean = mob.state != EntityState.ACTIVE || ++ticks >= 3
    }
}
