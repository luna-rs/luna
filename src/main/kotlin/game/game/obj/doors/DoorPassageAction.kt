package game.obj.doors

import api.predef.ext.*
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import java.util.concurrent.CompletableFuture

/**
 * Walks to a conditional door, waits for other crossings and checks access before stepping through.
 * Walking away cancels this weak action, matching the Mining Guild and Al Kharid crossings.
 *
 * @author lare96
 */
abstract class DoorPassageAction(plr: Player, door: GameObject, private val type: DoorType,
                                 private val faceDoor: Boolean = false) : Action<Player>(plr, ActionType.WEAK) {

    private val position = door.position
    private val direction = door.direction
    private val entering = Doors.isEntering(position, direction, plr.position)
    private val start = if (entering) position else position.translate(1, direction.toNormalDirection())
    private var walking: CompletableFuture<NavigationResult>? = null
    private var ticks = 0

    /**
     * Checks access at the door immediately before crossing. Returning false finishes the action.
     */
    protected abstract fun checkAccess(entering: Boolean): Boolean

    override fun run(): Boolean {
        if (++ticks > 30) {
            mob.sendMessage("You can't get through the door right now.")
            return true
        }
        val pending = walking
        if (pending != null && !pending.isDone) {
            return false
        }
        if (mob.position != start) {
            if (pending != null) {
                mob.sendMessage("You can't reach the door from here.")
                return true
            }
            walking = mob.navigator.navigate(start, false)
            return false
        }
        if (Doors.isPassageBusy(position)) {
            return false
        }
        val closed = world.objects.findAll(position)
            .filter { it.id == type.closed && it.direction == direction }.findFirst().orElse(null) ?: return true
        if (!checkAccess(entering)) {
            return true
        }
        if (Doors.passThrough(world, mob, closed, type, entering) && faceDoor) {
            mob.interact(closed)
        }
        return true
    }
}
