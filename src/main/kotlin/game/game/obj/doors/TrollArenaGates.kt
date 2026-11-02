package game.obj.doors

import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType

/**
 * The four arena gate leaves on Troll Stronghold's mountain approach, using the source's temporary passage.
 * Each replaces itself with invisible wall83 and an inactive3784 leaf for three ticks, retaining its forced rotation.
 * TODO Apply Troll Stronghold's Dad encounter state when that quest is implemented; no completion is fabricated here.
 *
 * @author lare96
 */
object TrollArenaGates {

    /**
     * A real gate placement and the open-leaf direction specified by Lost City's quest_troll script.
     *
     * @author lare96
     */
    data class Gate(val id: Int, val position: Position, val direction: ObjectDirection,
                    val openDirection: ObjectDirection) {
        val type = DoorType(id, 3784, null, Sound.IRON_DOOR_OPEN, null, 3)
    }

    val GATES = listOf(
        Gate(3782, Position(2897, 3618), ObjectDirection.WEST, ObjectDirection.SOUTH),
        Gate(3783, Position(2897, 3619), ObjectDirection.WEST, ObjectDirection.NORTH),
        Gate(3785, Position(2916, 3629), ObjectDirection.SOUTH, ObjectDirection.WEST),
        Gate(3786, Position(2917, 3629), ObjectDirection.SOUTH, ObjectDirection.EAST))

    /**
     * Uses the ordinary weak door approach and passage ownership, including queued walking cancellation.
     */
    fun cross(player: Player, obj: GameObject) {
        if (player.state != EntityState.ACTIVE || !player.isAlive || player.isLocked) return
        val gate = GATES.firstOrNull {
            it.id == obj.id && it.position == obj.position && it.direction == obj.direction &&
                obj.objectType == ObjectType.STRAIGHT_WALL
        } ?: return
        player.submitAction(object : DoorPassageAction(player, obj, gate.type, openDirection = gate.openDirection) {
            override fun checkAccess(entering: Boolean) =
                mob.state == EntityState.ACTIVE && mob.isAlive && !mob.isLocked
        })
    }
}
