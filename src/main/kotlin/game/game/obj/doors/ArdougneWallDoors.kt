package game.obj.doors

import api.predef.ext.*
import game.player.Sound
import game.skill.agility.AgilityAction
import game.skill.agility.AgilityApproach
import game.skill.agility.AgilityStep
import io.luna.game.action.DeathInterruptible
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableMessage
import io.luna.game.model.chunk.ChunkUpdatableRequest
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.ExactMovement
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import io.luna.net.msg.out.AddObjectMessageWriter
import io.luna.net.msg.out.RemoveObjectMessageWriter

/**
 * The two native lanes through West Ardougne's thick city wall, approached from either exterior face.
 * Revision377 places the four clickable leaves on two terrain-blocked tiles, so they require a scripted passage
 * rather than an ordinary walking door. The outer stands are cache-verified; the three-tick walking presentation
 * is reconstructed because the original opening choreography is unrecovered. Like the Barrows tunnel doors,
 * private chunk updates hide the four leaves during passage and restore them for that player afterward; world
 * objects and collision remain intact, allowing concurrent players to own their separate opening visuals.
 * TODO Restore Biohazard completion admission when that quest is implemented. Its temporary waiver charges no
 * fare and grants no quest progress, items or rewards.
 *
 * @author lare96
 */
object ArdougneWallDoors {
    val IDS = setOf(8738, 8739)
    val POLICY = InteractionPolicy(InteractionType.DISTANCE, 2)

    /**
     * One clickable exterior face and its walkable start and destination beyond the two blocked wall tiles.
     *
     * @author lare96
     */
    data class Passage(val id: Int, val position: Position, val from: Position, val to: Position,
                       val direction: ObjectDirection)

    val PASSAGES = listOf(
        Passage(8738, Position(2557, 3300), Position(2556, 3300), Position(2559, 3300), ObjectDirection.EAST),
        Passage(8739, Position(2557, 3299), Position(2556, 3299), Position(2559, 3299), ObjectDirection.EAST),
        Passage(8738, Position(2558, 3299), Position(2559, 3299), Position(2556, 3299), ObjectDirection.WEST),
        Passage(8739, Position(2558, 3300), Position(2559, 3300), Position(2556, 3300), ObjectDirection.WEST))

    /**
     * Approaches the clicked exterior face without turning its blocked wall into an unrestricted walking route.
     */
    fun use(player: Player, obj: GameObject) {
        val passage = PASSAGES.firstOrNull {
            it.id == obj.id && it.position == obj.position && it.direction == obj.direction
        } ?: return
        if (!ready(player) || !validObject(player, obj) || !onSide(player.position, passage) ||
            player.position.computeLongestDistance(passage.from) > AgilityApproach.RADIUS) return
        val begin = begin@ {
            if (!ready(player) || player.position != passage.from || !validObject(player, obj)) return@begin
            val leaves = PASSAGES.map { leaf ->
                player.world.objects.findAll(leaf.position).filter {
                    it.id == leaf.id && it.direction == leaf.direction && validObject(player, it)
                }.findFirst().orElse(null) ?: return@begin
            }
            player.submitAction(PassageAction(player, leaves, passage))
        }
        if (player.position == passage.from) begin() else {
            player.submitAction(AgilityApproach(player, passage.from) { begin() })
        }
    }

    /**
     * Holds the actor's original stand until passage completes, and cancels before a newer death lock is acquired.
     *
     * @author lare96
     */
    internal class PassageAction(player: Player, leaves: List<GameObject>, passage: Passage) :
        AgilityAction(player, listOf(AgilityStep(passage.to, 3, animation = 819)), null,
            start = { leaves.forEach { display(player, it, false) }; player.playSound(Sound.DOOR_OPEN) },
            valid = { alive(player) && player.position == passage.from && leaves.all { validObject(player, it) } }), DeathInterruptible {
        init {
            onSuccess = { restore(player, leaves) }
            onCancel = {
                player.exactMove(ExactMovement(player.lastRegion, player.position, player.position, 0, 1,
                    Direction.between(passage.from, passage.to)))
                restore(player, leaves)
            }
        }
    }

    private fun restore(player: Player, leaves: List<GameObject>) {
        if (player.state != EntityState.ACTIVE) return
        leaves.filter { validObject(player, it) }.forEach { display(player, it, true) }
    }

    private fun display(player: Player, original: GameObject, show: Boolean) {
        val visual = GameObject.createDynamic(player.context, original.id, original.position, original.objectType,
            original.direction, ChunkUpdatableView.localView(player))
        val chunk = visual.position.chunk
        val offset = chunk.offset(visual.position)
        val message: ChunkUpdatableMessage = if (show) AddObjectMessageWriter(visual.id, visual.objectType.id, visual.direction.id, offset)
        else RemoveObjectMessageWriter(visual.objectType.id, visual.direction.id, offset)
        player.world.chunks.load(chunk).queueUpdate(ChunkUpdatableRequest(visual, message, false))
    }

    private fun onSide(from: Position, passage: Passage) = from.z == passage.from.z &&
        if (passage.from.x < passage.to.x) from.x <= passage.from.x else from.x >= passage.from.x

    private fun alive(player: Player) = player.state == EntityState.ACTIVE && player.isAlive &&
        player.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }

    private fun ready(player: Player) = alive(player) && !player.isLocked && !player.walking.isLocked &&
        !player.status.isImmobilized()

    private fun validObject(player: Player, obj: GameObject) = obj.objectType == ObjectType.DEFAULT &&
        obj.state == EntityState.ACTIVE && !obj.isHidden && obj.isVisibleTo(player) &&
        player.world.objects.findAll(obj.position).anyMatch { it === obj }
}
