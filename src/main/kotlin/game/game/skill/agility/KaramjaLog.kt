package game.skill.agility

/*
 * Adapted from Lost City scripts/skill_agility/scripts/shortcuts.rs2, zq_logbalance.
 * MIT License
 * Copyright (c) 2023-2026 Lost City
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import game.skill.Skills
import io.luna.game.action.DeathInterruptible
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.LocalGraphic
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.ExactMovement
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType

/**
 * Karamja's river log, using Lost City's zq_logbalance movement, success roll and native wash-up positions.
 * No level or quest admission applies. A fall still reaches the opposite bank, with two XP and current-HP damage.
 *
 * @author lare96
 */
object KaramjaLog {
    const val ID = 2332

    /**
     * The raw plane-one log end, ground-plane stand, successful landing and native failed-crossing shore.
     *
     * @author lare96
     */
    data class Passage(val position: Position, val from: Position, val to: Position, val washUp: Position,
                       val turn: Position, val riverEnd: Position)

    val PASSAGES = listOf(
        Passage(Position(2907, 3049, 1), Position(2906, 3049), Position(2910, 3049), Position(2909, 3059),
            Position(2909, 3056), Position(2909, 3058)),
        Passage(Position(2909, 3049, 1), Position(2910, 3049), Position(2906, 3049), Position(2903, 3057),
            Position(2907, 3055), Position(2905, 3056)))

    /**
     * Aligns to the selected riverbank, then runs either the crossing or its original failure without course credit.
     */
    fun cross(player: Player, obj: GameObject,
              succeeds: (Int) -> Boolean = { Skills.success(90, 250, minOf(it, 99)) }) {
        val passage = PASSAGES.firstOrNull { it.position == obj.position } ?: return
        if (!ready(player) || !validObject(player, obj) ||
            player.position.computeLongestDistance(passage.from) > AgilityApproach.RADIUS) return
        val begin = begin@ {
            if (!ready(player) || player.position != passage.from || !validObject(player, obj)) return@begin
            val failed = !succeeds(player.agility.level)
            val direction = if (passage.to.x > passage.from.x) 1 else -1
            val middle = passage.from.translate(2 * direction, 0)
            val steps = mutableListOf(AgilityStep(middle, 1, walkingAnimation = 762, jump = true,
                begin = { player.move(middle) }))
            if (failed) {
                steps += listOf(
                    AgilityStep(middle, 1, walkingAnimation = 766, jump = true,
                        message = "You lose your footing and fall into the water.", begin = {
                            player.animation(Animation(764, 5, Animation.AnimationPriority.NORMAL))
                            player.face(middle.translate(0, 2))
                        }),
                    AgilityStep(Position(2908, 3050), 1, walkingAnimation = AgilityWalk.SWIM.walk, jump = true,
                        sounds = listOf(AgilitySound(Sound.POOL_PLOP)), begin = {
                            player.exactMove(ExactMovement(player.lastRegion, Position(2908, 3049),
                                Position(2908, 3050), 6, 9, Direction.NORTH))
                            LocalGraphic(player.context, 68, 0, 3, Position(2908, 3050),
                                ChunkUpdatableView.globalView()).display()
                        }),
                    AgilityStep(Position(2908, 3052), 2, walkingAnimation = AgilityWalk.SWIM.walk, jump = true,
                        message = "You're being washed down the river.", begin = { player.move(Position(2908, 3052)) }),
                    AgilityStep(Position(2908, 3054), 2, walkingAnimation = AgilityWalk.SWIM.walk, jump = true,
                        begin = { player.move(Position(2908, 3054)) }),
                    AgilityStep(passage.turn, 2, walkingAnimation = AgilityWalk.SWIM.walk, jump = true,
                        message = "You feel like you're drowning...", begin = { player.move(passage.turn) }),
                    AgilityStep(passage.riverEnd, 2, walkingAnimation = AgilityWalk.SWIM.walk, jump = true,
                        message = "You finally come to shore.", begin = { player.move(passage.riverEnd) }),
                    AgilityStep(passage.washUp, passage.riverEnd.computeLongestDistance(passage.washUp),
                        walkingAnimation = AgilityWalk.NORMAL))
            } else {
                steps += AgilityStep(passage.to, 3, walkingAnimation = 762, jump = true,
                    message = "You make it across the log without any problems.", begin = { player.move(passage.to) })
            }
            player.submitAction(object : AgilityAction(player, steps, "You attempt to walk across the slippery log.",
                valid = { player.state == EntityState.ACTIVE && player.isAlive }), DeathInterruptible {
                init {
                    onSuccess = {
                        player.agility.addExperience(if (failed) 2.0 else 4.0)
                        if (failed) player.damage(player.hitpoints.level * 6 / 100 + 1)
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

    private fun validObject(player: Player, obj: GameObject) = obj.id == ID &&
        obj.objectType == ObjectType.GROUND_DECORATION && obj.direction == ObjectDirection.NORTH &&
        player.world.collisionManager.isLoweredOnto(obj, player.z) &&
        obj.state == EntityState.ACTIVE && !obj.isHidden &&
        player.isViewableFrom(Position(obj.x, obj.y, player.z)) &&
        (obj.isGlobal || obj.view.isViewableFor(player)) &&
        player.world.objects.findAll(obj.position).anyMatch { it === obj }
}
