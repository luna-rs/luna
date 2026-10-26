/*
 * Gameplay rules and identifiers adapted from LostCityRS/Content, commit 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
 * MIT License
 *
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
 *
 */
package game.content.brimhavenArena

import api.predef.*
import game.skill.Skills
import game.skill.agility.AgilityStep
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.Entity
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import java.util.concurrent.CompletableFuture

/**
 * Native crossings between platforms, including both directions and the lower-floor failure landings.
 *
 * @author lare96
 */
object ArenaObstacles {
    val ids = setOf(3551, 3553, 3555, 3557, 3559, 3561, 3564, 3565, 3566, 3570, 3571, 3572, 3578, 3583)
    private val ropeReach = InteractionPolicy(InteractionType.SIZE, 2)

    fun policy(target: Entity): InteractionPolicy =
        if ((target as? GameObject)?.id == 3566) ropeReach else InteractionPolicy.STANDARD_SIZE

    /**
     * A crossing outcome is selected once at its start, with XP deferred until the last movement completes.
     *
     * @author lare96
     */
    data class Route(val start: Position, val steps: List<AgilityStep>, val xp: Double, val failed: Boolean,
                     val hurts: Boolean = failed)

    internal fun route(player: Player, obj: GameObject, roll: Double): Route? {
        if (obj.id !in ids || obj.position.z != 3 || !BrimhavenArena.inside(obj.position)) return null
        val p = obj.position
        var dx = 0
        var dy = 0
        var start: Position
        var length = 7
        var xp: Double
        var low = 230
        var high = 280
        var walking = 762
        var animation = -1
        var jump = false
        var reverse = false
        when (obj.id) {
            3551 -> { dy = if (obj.direction == ObjectDirection.EAST) -1 else 1; start = p; xp = 10.0 }
            3566 -> {
                when (obj.direction) {
                    ObjectDirection.WEST -> dy = -1
                    ObjectDirection.EAST -> dy = 1
                    ObjectDirection.NORTH -> dx = -1
                    ObjectDirection.SOUTH -> dx = 1
                }
                start = p.translate(-dx * 2, -dy * 2)
                length = 5; xp = 20.0; low = 225; high = 285; walking = -1; animation = 751
            }
            3564 -> {
                when (obj.direction) {
                    ObjectDirection.WEST -> dy = -1
                    ObjectDirection.EAST -> dy = 1
                    ObjectDirection.NORTH -> dx = -1
                    ObjectDirection.SOUTH -> dx = 1
                }
                start = p.translate(if (dy != 0) 1 else 0, if (dx != 0) 1 else 0)
                length = 8; xp = 14.0; low = 235; high = 275; walking = 744; animation = 742
            }
            3583 -> {
                if (obj.direction == ObjectDirection.WEST) dy = if (p.y == 9559) 1 else -1
                else dx = if (p.x == 2785) 1 else -1
                start = p; xp = 22.0; low = 185; high = 255; walking = -1
                reverse = dy == -1 || dx == 1 && obj.direction == ObjectDirection.SOUTH ||
                    dx == -1 && obj.direction == ObjectDirection.NORTH
                animation = if (reverse) 1122 else 1118
            }
            3565 -> {
                // Low walls are two tiles wide; select the end on the player's own platform.
                if (obj.direction == ObjectDirection.WEST) dy = if (player.y <= p.y) 1 else -1
                else dx = if (player.x <= p.x) 1 else -1
                start = if (dx > 0 || dy > 0) p.translate(-dx, -dy) else p.translate(-dx * 2, -dy * 2)
                length = 3; xp = 8.0; low = 240; high = 290; walking = -1; animation = 1252
            }
            else -> {
                // These endpoints sit three tiles away from the eleven-tile platform grid.
                val horizontal = obj.id in setOf(3559, 3561, 3570, 3571, 3572) ||
                    Math.floorMod(p.x - 2761, 11) in setOf(3, 8)
                if (horizontal) dx = if (Math.floorMod(p.x - 2761, 11) == 3) 1 else -1
                else dy = if (Math.floorMod(p.y - 9546, 11) == 3) 1 else -1
                start = p.translate(-dx, -dy)
                when (obj.id) {
                    3559, 3561 -> { xp = 16.0; walking = if (dx == 1) 754 else 756; low = 256; high = 256 }
                    3570, 3571, 3572 -> { xp = 6.0; walking = -1; low = 256; high = 256 }
                    3578 -> { xp = 18.0; walking = -1; animation = 741; jump = true }
                    else -> xp = 12.0
                }
            }
        }
        val failed = if (obj.id in 3570..3572) ArenaTraps.broken(start.translate(dx * 2, dy * 2))
            else roll >= Skills.successRate(low, high, player.agility.level.coerceIn(1, 99))
        fun at(distance: Int, z: Int = 3) = Position(start.x + dx * distance, start.y + dy * distance, z)
        if (failed) {
            if (obj.id == 3565) return Route(start,
                listOf(AgilityStep(at(1), 1), AgilityStep(start, 3, animation = 1106, jump = true)), 0.0, true, false)
            val landing = when (obj.id) {
                3566 -> at(0, 0)
                3551 -> at(1, 0).translate(-dy, 0)
                3578 -> at(2, 0).translate(-dy * 2, dx * 2)
                3583, 3570, 3571, 3572 -> at(1, 0)
                3564 -> at(2, 0).translate(0, -dy)
                else -> at(1, 0).translate(-dy, dx)
            }
            val failAnimation = when (obj.id) {
                3566 -> 1105
                3583 -> if (reverse) 1123 else 1119
                3564 -> 743
                3570, 3571, 3572 -> 768
                else -> 764
            }
            return Route(start, listOf(AgilityStep(at(if (obj.id == 3566) 1 else 2), 2,
                walkingAnimation = walking, animation = animation),
                AgilityStep(landing, 2, animation = failAnimation, jump = true)), 0.0, true)
        }
        val steps = when (obj.id) {
            3578 -> (1..7).map { AgilityStep(at(it), 2, animation = 741, jump = true) }
            3583 -> (1..7).map { AgilityStep(at(it), 2, animation = when (it) {
                1 -> if (reverse) 1121 else 1117
                7 -> if (reverse) 1124 else 1120
                else -> animation
            }) }
            3566, 3565 -> listOf(AgilityStep(at(length), if (obj.id == 3565) 5 else 4, animation = animation))
            else -> listOf(AgilityStep(at(2), 2, walkingAnimation = walking, animation = animation),
                AgilityStep(at(length), length - 2, walkingAnimation = walking, jump = jump))
        }
        return Route(start, steps, xp, false)
    }

    fun cross(player: Player, obj: GameObject, roll: Double = rand().nextDouble()): Boolean {
        if (!BrimhavenArena.ready(player) || BrimhavenArena.session(player) == null || player.z != 3 ||
            obj.state != EntityState.ACTIVE || !player.position.isWithinDistance(obj.position, 3)) return false
        val route = route(player, obj, roll) ?: return false
        if (player.position != route.start) {
            if (!player.position.isWithinDistance(route.start, 3)) return false
            if (player.actions.first(Approach::class.java) == null) player.submitAction(Approach(player, obj, route.start))
            return true
        }
        val session = BrimhavenArena.session(player)
        return BrimhavenArena.traverse(player, route.steps,
            if (route.failed) "You lose your footing!" else "You cross the obstacle.") {
            if (BrimhavenArena.session(player) === session && BrimhavenArena.inside(player.position)) {
                if (route.hurts) player.damage(player.hitpoints.level * 5 / 100 + 2)
                if (route.xp > 0) player.agility.addExperience(route.xp)
            }
        }
    }

    /**
     * Aligns a click with its native takeoff tile through collision-checked movement before locking the crossing.
     *
     * @author lare96
     */
    private class Approach(player: Player, val obj: GameObject, val start: Position) : Action<Player>(player, ActionType.WEAK) {
        private var pending: CompletableFuture<NavigationResult>? = null
        private var ticks = 0
        override fun run(): Boolean {
            if (++ticks > 30 || !BrimhavenArena.ready(mob) || BrimhavenArena.session(mob) == null ||
                obj.state != EntityState.ACTIVE) return true
            if (mob.position == start) { cross(mob, obj); return true }
            if (pending == null) pending = mob.navigator.navigate(start, false)
            return pending!!.isDone
        }
        override fun onFinished() {
            if (pending != null && mob.navigator.currentPending === pending) { mob.navigator.cancel(); mob.walking.clear() }
        }
    }

    internal fun cancelApproach(player: Player) { player.actions.first(Approach::class.java)?.interrupt() }
}
