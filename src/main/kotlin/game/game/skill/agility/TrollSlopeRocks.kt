package game.skill.agility

/*
 * Adapted from Lost City scripts/quests/quest_troll/scripts/quest_troll.rs2.
 */

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import game.skill.Skills
import io.luna.game.action.DeathInterruptible
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectType

/**
 * Native top and bottom rock slopes on the path between the ordinary Troll climbing rocks and Dad's arena.
 * Both require current Agility 15 and award one XP. A failed ascent falls back; a failed descent reaches its far side.
 *
 * @author lare96
 */
object TrollSlopeRocks {
    const val TOP = 3790
    const val BOTTOM = 3791
    const val LEVEL = 15

    /**
     * A source-placed slope end, its adjacent approach and the native three-tile displacement.
     *
     * @author lare96
     */
    data class Passage(val id: Int, val position: Position, val from: Position, val to: Position) {
        val down: Boolean get() = id == TOP
    }

    val PASSAGES = (3626..3627).flatMap { y ->
        listOf(Passage(TOP, Position(2859, y), Position(2858, y), Position(2861, y)),
            Passage(BOTTOM, Position(2860, y), Position(2861, y), Position(2858, y)))
    } + (3622..3623).flatMap { y ->
        listOf(Passage(TOP, Position(2879, y), Position(2880, y), Position(2877, y)),
            Passage(BOTTOM, Position(2878, y), Position(2877, y), Position(2880, y)))
    }

    /**
     * Preserves the native level check, 125/250 balance roll, climbing animations, failure displacement and damage.
     */
    fun climb(player: Player, obj: GameObject,
              succeeds: (Int) -> Boolean = { Skills.success(125, 250, minOf(it, 99)) }) {
        val passage = PASSAGES.firstOrNull { it.id == obj.id && it.position == obj.position } ?: return
        if (!ready(player) || !validObject(player, obj) ||
            player.position.computeLongestDistance(passage.from) > AgilityApproach.RADIUS) return
        val begin = begin@ {
            if (!ready(player) || player.position != passage.from || !validObject(player, obj) ||
                !Agility.checkLevel(player, LEVEL)) return@begin
            val failed = !succeeds(player.agility.level)
            val steps = mutableListOf(AgilityStep(passage.from, 1, jump = true))
            if (passage.down) {
                steps += AgilityStep(passage.to, 1, moveStart = 0, moveEnd = 90,
                    begin = { player.animation(Animation(740, 15, Animation.AnimationPriority.NORMAL)) })
                steps += AgilityStep(passage.to, 2, jump = true,
                    animation = if (failed) 739 else -1,
                    sounds = if (failed) listOf(AgilitySound(Sound.STUMBLE_LOOP, loops = 10)) else emptyList())
            } else {
                val direction = if (passage.to.x > passage.from.x) 1 else -1
                val middle = passage.from.translate(2 * direction, 0)
                steps += AgilityStep(middle, 2, walkingAnimation = AgilityWalk.CLIMB.walk)
                steps += if (failed) {
                    AgilityStep(passage.from, 2, animation = 739, moveStart = 0, moveEnd = 50,
                        sounds = listOf(AgilitySound(Sound.STUMBLE_LOOP, loops = 10)))
                } else AgilityStep(passage.to, 1, walkingAnimation = AgilityWalk.CLIMB.walk)
            }
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
        if (player.position == passage.from) begin() else {
            player.submitAction(AgilityApproach(player, passage.from) { begin() })
        }
    }

    private fun ready(player: Player) = player.state == EntityState.ACTIVE && player.isAlive && !player.isLocked &&
        !player.walking.isLocked && !player.status.isImmobilized()

    private fun validObject(player: Player, obj: GameObject) = obj.objectType == ObjectType.GROUND_DECORATION &&
        obj.state == EntityState.ACTIVE && !obj.isHidden && obj.isVisibleTo(player) &&
        player.world.objects.findAll(obj.position).anyMatch { it === obj }
}
