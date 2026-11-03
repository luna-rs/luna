package game.obj.doors

import api.predef.ext.*
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType

/**
 * The two revision377 courtyard doors around the Mourner Headquarters. The source2037 open leaf has no menu, so
 * opening either side uses the existing temporary door passage and restores the closed leaf after three ticks.
 * The duration follows Luna's ordinary temporary passages; the original quest script's delay is unrecovered.
 * TODO Restore the quests' headquarters admission and mourner disguise checks when their progress is implemented.
 * The temporary waiver does not grant quest state or enable disguise-triggered guard hostility.
 *
 * @author lare96
 */
object MournerHeadquartersDoors {
    const val ID = 2036
    const val OPEN_ID = 2037
    const val RESTORE_TICKS = 3

    /**
     * A native courtyard door and the walkable tiles on its two sides, verified against the revision377 cache.
     *
     * @author lare96
     */
    data class Passage(val position: Position, val from: Position, val to: Position, val direction: ObjectDirection)

    val PASSAGES = listOf(
        Passage(Position(2551, 3320), Position(2551, 3320), Position(2551, 3321), ObjectDirection.NORTH),
        Passage(Position(2551, 3328), Position(2551, 3328), Position(2551, 3327), ObjectDirection.SOUTH))

    private val type = DoorType(ID, OPEN_ID, null, null, null, RESTORE_TICKS)

    /**
     * Uses an actual visible closed door and the normal weak approach; opening from either side steps through it.
     */
    fun use(player: Player, obj: GameObject) {
        if (!ready(player) || !validObject(player, obj)) return
        player.submitAction(object : DoorPassageAction(player, obj, type) {
            override fun checkAccess(entering: Boolean) = ready(mob) && validObject(mob, obj)
        })
    }

    private fun ready(player: Player) = player.state == EntityState.ACTIVE && player.isAlive && !player.isLocked &&
        !player.walking.isLocked && !player.status.isImmobilized() &&
        player.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }

    private fun validObject(player: Player, obj: GameObject) = obj.id == ID &&
        obj.objectType == ObjectType.STRAIGHT_WALL && obj.state == EntityState.ACTIVE && !obj.isHidden &&
        obj.isVisibleTo(player) && PASSAGES.any { it.position == obj.position && it.direction == obj.direction } &&
        player.world.objects.findAll(obj.position).anyMatch { it === obj }
}
