package game.skill.agility

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import game.skill.Skills
import io.luna.game.action.DeathInterruptible
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.ExactMovement
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectType

/**
 * The two pairs of climbing rocks on the path up Trollheim, a shortcut for current Agility 15 that awards one XP
 * either way. The climb up crosses the rocks a tile a tick, and a slip slides the player back down. The climb down
 * drops both tiles facing the rocks, and a slip still lands at the bottom. A slip deals 5% of current Hitpoints
 * plus one.
 *
 * @author TheLining
 */
object TrollheimMountainRocks {
    val IDS = setOf(3803, 3804)
    const val LEVEL = 15

    /**
     * A placed rock and the tiles below and above it.
     *
     * @author TheLining
     */
    data class Passage(val position: Position, val bottom: Position, val top: Position, val objectId: Int) {
        val facing: Direction get() = Direction.between(bottom, top)
    }

    val PASSAGES = listOf(
        Passage(Position(2887, 3661), Position(2887, 3660), Position(2887, 3662), 3804),
        Passage(Position(2888, 3661), Position(2888, 3660), Position(2888, 3662), 3803),
        Passage(Position(2885, 3683), Position(2884, 3683), Position(2886, 3683), 3803),
        Passage(Position(2885, 3684), Position(2884, 3684), Position(2886, 3684), 3804))

    /**
     * Climbs [obj] from the side [player] is nearer, after walking them onto it, with the 125/250 balance roll.
     */
    fun climb(player: Player, obj: GameObject,
              succeeds: (Int) -> Boolean = { Skills.success(125, 250, minOf(it, 99)) }) {
        val passage = PASSAGES.firstOrNull { it.position == obj.position } ?: return
        if (obj.id !in IDS || !ready(player) || !validObject(player, obj)) return
        val from = listOf(passage.bottom, passage.top).minBy { player.position.computeLongestDistance(it) }
        if (player.position.computeLongestDistance(from) > AgilityApproach.RADIUS) return
        val begin = begin@ {
            if (!ready(player) || player.position != from || !validObject(player, obj) ||
                !Agility.checkLevel(player, LEVEL)) return@begin
            val failed = !succeeds(player.agility.level)
            val steps = if (from == passage.bottom) climbUp(player, passage, failed)
            else climbDown(player, passage, failed)
            player.submitAction(object : AgilityAction(player, steps, null,
                valid = { player.state == EntityState.ACTIVE && player.isAlive }), DeathInterruptible {
                init {
                    onSuccess = {
                        player.animation(Animation(-1))
                        player.agility.addExperience(1.0)
                        if (failed) {
                            player.sendMessage("You fall and hurt yourself.")
                            player.speak("Ouch")
                            player.damage(player.hitpoints.level * 5 / 100 + 1)
                        }
                    }
                }
            })
        }
        if (player.position == from) begin() else player.submitAction(AgilityApproach(player, from) { begin() })
    }

    /**
     * Climbs onto the rock and on to the top, or slides back to the bottom from the rock.
     */
    private fun climbUp(player: Player, passage: Passage, failed: Boolean) = listOf(
        AgilityStep(passage.bottom, 1, jump = true),
        AgilityStep(passage.position, 1, walkingAnimation = AgilityWalk.CLIMB.walk),
        if (failed) {
            AgilityStep(passage.bottom, 2, animation = 739, jump = true,
                sounds = listOf(AgilitySound(Sound.STUMBLE_LOOP, loops = 10)),
                begin = { slide(player, passage.position, passage.bottom, 25, passage.facing) })
        } else AgilityStep(passage.top, 1, walkingAnimation = AgilityWalk.CLIMB.walk))

    /**
     * Climbs down both tiles to the bottom, where a slip falls.
     */
    private fun climbDown(player: Player, passage: Passage, failed: Boolean) = listOf(
        AgilityStep(passage.top, 1, jump = true),
        AgilityStep(passage.bottom, 1, jump = true, begin = {
            slide(player, passage.top, passage.bottom, 50, passage.facing)
            player.animation(Animation(740))
        }),
        if (failed) {
            AgilityStep(passage.bottom, 2, animation = 739, jump = true,
                sounds = listOf(AgilitySound(Sound.STUMBLE_LOOP, loops = 10)))
        } else AgilityStep(passage.bottom, 1, jump = true))

    // Agility legs face the way they move; these moves face up the rocks instead.
    private fun slide(player: Player, from: Position, to: Position, end: Int, facing: Direction) =
        player.exactMove(ExactMovement(player.lastRegion, from, to, 0, end, facing))

    private fun ready(player: Player) = player.state == EntityState.ACTIVE && player.isAlive && !player.isLocked &&
        !player.walking.isLocked && !player.status.isImmobilized()

    private fun validObject(player: Player, obj: GameObject) = obj.objectType == ObjectType.GROUND_DECORATION &&
        obj.state == EntityState.ACTIVE && !obj.isHidden && obj.isVisibleTo(player) &&
        player.world.objects.findAll(obj.position).anyMatch { it === obj }
}
