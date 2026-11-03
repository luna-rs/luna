package game.skill.agility

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.DeathInterruptible
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.ExactMovement
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType

/**
 * The northern Gnome Stronghold rock shortcut, requiring current Agility37 at the start in either direction.
 * Both clickable decorations and the walkable opposite-side stands are verified against the revision377 cache.
 * The six-tick climb is reconstructed from their distance using the native Wilderness climbing animation737;
 * the original shortcut's movement script has not been recovered. There is no failure, damage or obstacle XP.
 * TODO Restore The Grand Tree's quest admission when that quest is implemented, without granting quest progress.
 *
 * @author lare96
 */
object GnomeStrongholdRocks {

    const val LEVEL = 37

    /**
     * The source-placed rock clicked from each walkable end of the shortcut.
     *
     * @author lare96
     */
    data class Passage(val id: Int, val position: Position, val from: Position, val to: Position,
                       val direction: ObjectDirection)

    val PASSAGES = listOf(
        Passage(9316, Position(2487, 3515), Position(2486, 3515), Position(2489, 3521), ObjectDirection.SOUTH),
        Passage(9317, Position(2489, 3520), Position(2489, 3521), Position(2486, 3515), ObjectDirection.WEST))

    /**
     * Uses the current level, including boosts and drains; a level change after the climb starts cannot fail it.
     */
    fun canTraverse(player: Player) = player.agility.level >= LEVEL

    /**
     * Aligns with the clicked end before checking Agility, then retains the original stand until the client climb
     * finishes. Displacement, death, removal or loss of visibility cancels it without moving or awarding anything.
     */
    fun climb(player: Player, obj: GameObject) {
        val passage = PASSAGES.firstOrNull {
            it.id == obj.id && it.position == obj.position && it.direction == obj.direction
        } ?: return
        if (!ready(player) || !validObject(player, obj) ||
            player.position.computeLongestDistance(passage.from) > AgilityApproach.RADIUS) return
        val begin = begin@ {
            if (!ready(player) || player.position != passage.from || !validObject(player, obj)) return@begin
            if (!Agility.checkLevel(player, LEVEL)) return@begin
            player.submitAction(ClimbAction(player, obj, passage))
        }
        if (player.position == passage.from) begin() else {
            player.submitAction(AgilityApproach(player, passage.from) { begin() })
        }
    }

    /**
     * Identifies this climb so a new death sequence can cancel its lock before acquiring the death lock.
     *
     * @author lare96
     */
    internal class ClimbAction(player: Player, obj: GameObject, passage: Passage) :
        AgilityAction(player, listOf(AgilityStep(passage.to, 6, animation = AgilityWalk.CLIMB.walk)),
            "You climb the rocks...",
            valid = { alive(player) && player.position == passage.from && validObject(player, obj) }), DeathInterruptible {
        init {
            onCancel = {
                player.exactMove(ExactMovement(player.lastRegion, player.position, player.position, 0, 1,
                    Direction.between(passage.from, passage.to)))
            }
        }
    }

    private fun alive(player: Player) = player.state == EntityState.ACTIVE && player.isAlive &&
        player.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }

    // Movement restrictions apply before admission; a freeze does not cancel an accepted climb.
    private fun ready(player: Player) = alive(player) && !player.isLocked && !player.walking.isLocked &&
        !player.status.isImmobilized()

    private fun validObject(player: Player, obj: GameObject) = obj.objectType == ObjectType.GROUND_DECORATION &&
        obj.state == EntityState.ACTIVE && !obj.isHidden && obj.isVisibleTo(player) &&
        player.world.objects.findAll(obj.position).anyMatch { it === obj }
}
