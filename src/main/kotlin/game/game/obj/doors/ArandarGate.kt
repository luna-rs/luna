package game.obj.doors

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import java.util.concurrent.CompletableFuture

/**
 * The large Arandar palisade, crossed through the middle after one leaf swings open.
 *
 * @author lare96
 */
object ArandarGate {

    const val LEFT = 3945
    const val RIGHT = 3944
    val LEFT_POSITION = Position(2384, 3334)
    val RIGHT_POSITION = Position(2386, 3334)
    val NORTH = Position(2385, 3335)
    val SOUTH = Position(2385, 3333)

    /**
     * Aligns the player with the passage before moving two tiles through the palisade.
     */
    fun open(plr: Player, gate: GameObject) {
        if ((gate.id != LEFT && gate.id != RIGHT) || plr.isLocked || !canTravel(plr) || plr.z != 0 ||
            !plr.position.isWithinDistance(gate.position, 3)) return
        val position = if (gate.id == LEFT) LEFT_POSITION else RIGHT_POSITION
        if (gate.position != position) return
        // TODO Once Regicide exists, entry from the north requires progress beyond killing Tyras; leaving stays free.
        if (plr.actions.first(Approach::class.java) == null) {
            plr.submitAction(Approach(plr, gate.id))
        }
    }

    /**
     * Opens only the selected leaf for six ticks, using its native open placement.
     */
    private fun cross(plr: Player, id: Int): Boolean {
        val position = if (id == LEFT) LEFT_POSITION else RIGHT_POSITION
        val gate = world.objects.findAll(position).filter { it.id == id }.findFirst().orElse(null) ?: return false
        if (!world.removeObject(gate)) return false
        val openPosition = if (id == LEFT) Position(2384, 3333) else Position(2387, 3333)
        val opened = world.addObject(if (id == LEFT) 3947 else 3946, openPosition,
            gate.objectType, ObjectDirection.EAST)
        world.scheduleOnce(6) {
            if (world.removeObject(opened)) {
                world.addObject(gate.id, gate.position, gate.objectType, gate.direction)
            }
        }
        plr.playSound(Sound.BIGDOOR_OPEN)
        return true
    }

    /**
     * Prevents a pending crossing from outliving its player or death sequence.
     */
    private fun canTravel(plr: Player): Boolean = plr.state == EntityState.ACTIVE && plr.isAlive &&
        plr.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }

    /**
     * Holds the centre passage for its one-tick native transfer after the chosen leaf opens.
     *
     * @author lare96
     */
    private class Passage(plr: Player, private val gate: Int, private val start: Position,
                          private val destination: Position) : LockedAction(plr) {
        override fun beforeLock(): Boolean = !mob.isLocked && canTravel(mob) && mob.position == start

        override fun onLock() {
            mob.walking.clear()
            if (!cross(mob, gate)) complete()
        }

        override fun run(): Boolean {
            if (canTravel(mob) && mob.position == start) mob.move(destination)
            return true
        }
    }

    /**
     * Approaches the centre without moving players across distant or blocked ground.
     *
     * @author lare96
     */
    private class Approach(plr: Player, private val gate: Int) : Action<Player>(plr, ActionType.WEAK) {
        private val fromNorth = plr.position.y > LEFT_POSITION.y
        private val start = if (fromNorth) NORTH else SOUTH
        private val destination = if (fromNorth) SOUTH else NORTH
        private var walking: CompletableFuture<NavigationResult>? = null
        private var ticks = 0

        override fun run(): Boolean {
            if (!canTravel(mob) || mob.isLocked || mob.z != start.z) return true
            if (++ticks > 30) {
                mob.sendMessage("You can't get through the palisade right now.")
                return true
            }
            val pending = walking
            if (pending != null && !pending.isDone) return false
            if (mob.position != start) {
                if (pending != null) return true
                walking = mob.navigator.navigate(start, false)
                return false
            }
            val position = if (gate == LEFT) LEFT_POSITION else RIGHT_POSITION
            if (world.objects.findAll(position).noneMatch { it.id == gate }) return false
            mob.submitAction(Passage(mob, gate, start, destination))
            return true
        }
    }
}
