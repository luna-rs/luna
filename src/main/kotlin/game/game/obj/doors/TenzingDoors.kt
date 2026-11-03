package game.obj.doors

import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType

/**
 * Tenzing's two native doors on the Death Plateau path, using the source's three-tick temporary passage.
 * The non-interactive 1532 leaf and invisible wall 83 restore without registering an ordinary open-door handler.
 * Admission is temporarily free, as for the mountain rocks, and grants no quest state or reward.
 * TODO Restore Death Plateau's spoken-Saba/spoken-Tenzing front-door and received-map back-door admission
 * when that quest is implemented, following the mountain rocks' existing deferred quest policy.
 *
 * @author lare96
 */
object TenzingDoors {

    /**
     * A source-placed straight door and its two legal native standing tiles.
     *
     * @author lare96
     */
    data class Passage(val id: Int, val position: Position, val direction: ObjectDirection,
                       val otherSide: Position) {
        val type = DoorType(id, 1532, null, Sound.DOOR_OPEN, null, 3)
    }

    val PASSAGES = listOf(
        Passage(3745, Position(2822, 3555), ObjectDirection.EAST, Position(2823, 3555)),
        Passage(3746, Position(2820, 3557), ObjectDirection.NORTH, Position(2820, 3558)))

    val IDS = PASSAGES.map { it.id }.toSet()

    /**
     * Uses the ordinary cancellable door approach and rechecks the actor before admitting a passage.
     */
    fun cross(player: Player, obj: GameObject) {
        if (!ready(player)) return
        val passage = PASSAGES.firstOrNull {
            it.id == obj.id && it.position == obj.position && it.direction == obj.direction &&
                obj.objectType == ObjectType.STRAIGHT_WALL
        } ?: return
        player.submitAction(object : DoorPassageAction(player, obj, passage.type) {
            override fun checkAccess(entering: Boolean) = ready(mob)
        })
    }

    private fun ready(player: Player) =
        player.state == EntityState.ACTIVE && player.isAlive && !player.isLocked
}
