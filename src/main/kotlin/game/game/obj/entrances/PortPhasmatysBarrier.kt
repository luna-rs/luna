package game.obj.entrances

import api.predef.ext.*
import game.skill.agility.AgilityAction
import game.skill.agility.AgilityApproach
import game.skill.agility.AgilityStep
import io.luna.game.action.DeathInterruptible
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.ExactMovement
import io.luna.game.model.mob.dialogue.TextDialogue
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType

/**
 * Port Phasmatys's two native energy barriers, with one ordinary Continue page before entry and a direct free exit.
 * The revision377 cache supplies their placements, rotations and two-tile footprints. Their opposite-side stands
 * and two-tick walking presentation are reconstructed from that geometry rather than a recovered journey script.
 * TODO Restore Ghosts Ahoy's entry progression and pre-completion ecto-token toll when the quest is implemented.
 * The temporary completed-quest admission requires no ghostspeak amulet and creates no quest state or rewards.
 *
 * @author lare96
 */
object PortPhasmatysBarrier {

    const val ID = 5259
    const val ENTRY_MESSAGE = "The guards allow you through the barrier."

    /**
     * Lets the content approach own both footprint lanes without the generic diagonal interaction step.
     */
    val POLICY = InteractionPolicy(InteractionType.DISTANCE, 2)

    /**
     * A walkable lane through one of the two revision377 barrier footprints.
     *
     * @author lare96
     */
    data class Passage(val position: Position, val outside: Position, val inside: Position,
                       val direction: ObjectDirection)

    val PASSAGES = listOf(
        Passage(Position(3652, 3485), Position(3651, 3485), Position(3653, 3485), ObjectDirection.EAST),
        Passage(Position(3652, 3485), Position(3651, 3486), Position(3653, 3486), ObjectDirection.EAST),
        Passage(Position(3659, 3508), Position(3659, 3509), Position(3659, 3507), ObjectDirection.SOUTH),
        Passage(Position(3659, 3508), Position(3660, 3509), Position(3660, 3507), ObjectDirection.SOUTH))

    /**
     * Approaches the closest lane on the clicked barrier's current side. The entry page never crosses on a normal
     * close, and continuing it rechecks its actor, original stand and the actual visible object.
     */
    fun use(player: Player, obj: GameObject) {
        if (!ready(player) || !validObject(player, obj)) return
        val matching = PASSAGES.filter { it.position == obj.position && it.direction == obj.direction }
        val entering = when (obj.direction) {
            ObjectDirection.EAST -> when {
                player.x < obj.position.x -> true
                player.x > obj.position.x -> false
                else -> return
            }
            ObjectDirection.SOUTH -> when {
                player.y > obj.position.y -> true
                player.y < obj.position.y -> false
                else -> return
            }
            else -> return
        }
        val passage = matching.minByOrNull {
            player.position.computeLongestDistance(if (entering) it.outside else it.inside)
        } ?: return
        val from = if (entering) passage.outside else passage.inside
        val to = if (entering) passage.inside else passage.outside
        if (player.position.computeLongestDistance(from) > AgilityApproach.RADIUS) return
        val begin = begin@ {
            if (!ready(player) || player.position != from || !validObject(player, obj)) return@begin
            if (!entering) {
                cross(player, obj, from, to)
            } else {
                var continued = false
                val page = TextDialogue(ENTRY_MESSAGE)
                page.setContinueAction {
                    if (!continued) {
                        continued = true
                        cross(player, obj, from, to)
                    }
                }
                player.overlays.open(page)
            }
        }
        if (player.position == from) begin() else player.submitAction(AgilityApproach(player, from) { begin() })
    }

    /**
     * Keeps the actor on its original server tile until the short client crossing finishes, preventing a stale
     * dialogue or an interrupted crossing from moving a displaced player back to this barrier.
     */
    private fun cross(player: Player, obj: GameObject, from: Position, to: Position) {
        if (!ready(player) || player.position != from || !validObject(player, obj)) return
        player.submitAction(PassageAction(player, obj, from, to))
    }

    /**
     * Identifies this crossing so a new death sequence can cancel its lock before acquiring the death lock.
     *
     * @author lare96
     */
    internal class PassageAction(player: Player, obj: GameObject, from: Position, to: Position) :
        AgilityAction(player, listOf(AgilityStep(to, 2, animation = 819)), null,
            valid = { alive(player) && player.position == from && validObject(player, obj) }), DeathInterruptible {
        init {
            onCancel = {
                player.exactMove(ExactMovement(player.lastRegion, player.position, player.position, 0, 1,
                    Direction.between(from, to)))
            }
        }
    }

    private fun alive(player: Player) = player.state == EntityState.ACTIVE && player.isAlive &&
        player.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }

    // Movement restrictions apply before admission; a freeze does not cancel an accepted crossing.
    private fun ready(player: Player) = alive(player) && !player.isLocked && !player.walking.isLocked &&
        !player.status.isImmobilized()

    private fun validObject(player: Player, obj: GameObject) = obj.id == ID &&
        obj.objectType == ObjectType.DEFAULT && obj.state == EntityState.ACTIVE && !obj.isHidden &&
        obj.isVisibleTo(player) && PASSAGES.any { it.position == obj.position && it.direction == obj.direction } &&
        player.world.objects.findAll(obj.position).anyMatch { it === obj }
}
