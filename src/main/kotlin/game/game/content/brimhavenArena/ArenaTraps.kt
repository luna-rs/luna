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
import api.predef.ext.*
import game.content.brimhavenArena.BrimhavenArena.tile
import game.player.Sound
import game.skill.Skills
import game.skill.agility.AgilityStep
import io.luna.game.model.LocalGraphic
import io.luna.game.model.LocalProjectile
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType

/**
 * Step-on hazards, timed blades and the two independently changing plank bridges.
 *
 * @author lare96
 */
object ArenaTraps {
    /**
     * One trap entry tile, with successful and failed landing points from the source scripts.
     *
     * @author lare96
     */
    data class Trap(val start: Position, val destination: Position, val failure: Position, val objectId: Int,
                    val anchor: Position, val level: Int, val low: Int, val high: Int, val xp: Double)

    val traps: Map<Position, Trap> = buildList {
        fun pair(x: Int, y: Int, dx: Int, dy: Int, id: Int) {
            val first = tile(x, y); val second = first.translate(dx, dy)
            for ((start, direction) in listOf(first to 1, second to -1)) {
                add(Trap(start, start.translate(dx * direction * 2, dy * direction * 2),
                    start.translate(-dx * direction, -dy * direction), id, start, 20,
                    if (id == 3582) 180 else 165, if (id == 3582) 250 else 235,
                    if (id == 3582) 24.0 else 26.0))
            }
        }
        pair(9, 37, 0, 1, 3582); pair(20, 15, 0, 1, 3582); pair(47, 32, 1, 0, 3582)
        pair(20, 48, 0, 1, 3585); pair(47, 21, 1, 0, 3585); pair(47, 43, 1, 0, 3585)
        fun saw(x: Int, y: Int, dx: Int, dy: Int, anchor: Position) {
            val first = tile(x, y)
            for (step in 0..2) {
                val forwards = step == 0
                add(Trap(first.translate(dx * step, dy * step),
                    first.translate(dx * (if (forwards) 3 else -1), dy * (if (forwards) 3 else -1)),
                    first.translate(dx * (if (forwards) -1 else 3), dy * (if (forwards) -1 else 3)),
                    3580, anchor, 40, 141, 191, 28.0))
            }
        }
        saw(25, 43, 1, 0, tile(25, 44)); saw(31, 37, 0, 1, tile(30, 37)); saw(25, 21, 1, 0, tile(25, 20))
        fun dart(x: Int, y: Int, px: Int, py: Int, tx: Int, ty: Int, fx: Int, fy: Int) {
            add(Trap(tile(x, y), tile(tx, ty), tile(fx, fy), 3581, tile(px, py), 40, 130, 180, 30.0))
        }
        dart(42, 38, 42, 32, 42, 36, 42, 39); dart(42, 37, 42, 43, 42, 39, 42, 36)
        dart(37, 21, 31, 21, 35, 21, 38, 21); dart(36, 21, 42, 21, 38, 21, 35, 21)
        dart(26, 32, 20, 32, 24, 32, 27, 32); dart(25, 32, 31, 32, 27, 32, 24, 32)
    }.associateBy { it.start }

    fun trigger(player: Player, roll: Double = rand().nextDouble()): Boolean {
        if (!BrimhavenArena.ready(player) || BrimhavenArena.session(player) == null) return false
        val trap = traps[player.position] ?: return false
        val obj = world.locator.findObjectsOnTile(trap.anchor) { it.id == trap.objectId }.firstOrNull() ?: return false
        val failed = player.agility.level < trap.level ||
            roll >= Skills.successRate(trap.low, trap.high, player.agility.level.coerceIn(1, 99))
        if (player.agility.level < trap.level) player.sendMessage("You need an Agility level of ${trap.level} to pass this trap.")
        val animation = if (failed) if (trap.objectId == 3585) 1125 else 1114
            else if (trap.objectId == 3581) 1110 else 1115
        val destination = if (failed) trap.failure else trap.destination
        val sound = when (trap.objectId) {
            3582 -> if (failed) Sound.FLOOR_SPIKES else Sound.FLOOR_SPIKES_AVOID
            3585 -> if (failed) Sound.ROOF_COLLAPSE else Sound.COLLAPSE_AVOID
            3581 -> if (failed) Sound.DART_FIRE_HIT else Sound.DART_FIRE_AVOID
            else -> Sound.BLADE_HIT
        }
        val session = BrimhavenArena.session(player)
        val steps = mutableListOf(AgilityStep(player.position, 1, jump = true))
        if (!failed && trap.objectId == 3581) steps += AgilityStep(player.position, 7, animation = 1110, jump = true)
        steps += AgilityStep(destination, 2, animation = animation)
        val started = BrimhavenArena.traverse(player, steps,
            if (failed) "The trap hits you!" else "You avoid the trap.") {
            if (BrimhavenArena.session(player) === session) {
                if (failed) {
                    if (trap.objectId == 3581) player.agility.adjustLevel(-2)
                    player.damage(player.hitpoints.level * 5 / 100 + 2)
                } else player.agility.addExperience(trap.xp)
            }
        }
        if (started) {
            player.playSound(sound)
            when (trap.objectId) {
                3582 -> obj.animate(1111)
                3580 -> obj.animate(1107)
                3585 -> LocalGraphic(world.context, 271, 0, 0, trap.start, ChunkUpdatableView.globalView()).display()
                3581 -> LocalProjectile.followPath(world.context).setSourcePosition(trap.anchor)
                    .setTargetPosition(trap.start).setId(270).setStartHeight(10).setEndHeight(10)
                    .setTiming(0, 40).setInitialSlope(0).build().display()
            }
        }
        return started
    }

    internal fun broken(position: Position): Boolean =
        world.locator.findObjectsOnTile(position) { it.id == 3576 }.isNotEmpty()

    /**
     * Each bridge has one intact lane. The two other lanes have broken outer tiles and random inner tiles.
     */
    internal fun plankLayout(safeRows: List<Int>, pick: (Int) -> Int = { rand().nextInt(it) }): Map<Position, Int> {
        require(safeRows.size == 2 && safeRows.all { it in 0..2 })
        return buildMap {
            for ((bridge, origin) in listOf(46 to 53, 13 to 20).withIndex()) {
                for (row in 0..2) for (column in 0..3) {
                    val intact = row == safeRows[bridge] || column in 1..2 && pick(2) == 0
                    put(tile(origin.first + column, origin.second + row), if (intact) 3573 + pick(3) else 3576)
                }
            }
        }
    }

    internal fun rebuildPlanks() {
        for ((position, id) in plankLayout(List(2) { rand().nextInt(3) })) {
            world.removeObject(position) { this.id in 3573..3576 }
            world.addObject(id, position, ObjectType.GROUND_DECORATION,
                if (rand().nextBoolean()) ObjectDirection.NORTH else ObjectDirection.SOUTH)
        }
    }

    internal val bladeTiles = listOf(tile(31, 15) to tile(31, 16), tile(9, 48) to tile(9, 49),
        tile(36, 43) to tile(37, 43))

    internal fun blades() {
        for ((first, second) in bladeTiles) {
            val floor = world.locator.findObjectsOnTile(first) { it.id == 3568 }.firstOrNull() ?: continue
            val blade = world.addObject(3567, first, ObjectType.DEFAULT, floor.direction)
            world.scheduleOnce(5) { world.removeObject(blade) }
            for (player in world.locator.findPlayers(first, 4) { it.position == first || it.position == second }) {
                if (!player.isAlive || BrimhavenArena.session(player) == null) continue
                val dest = if (player.position == first) first.translate(first.x - second.x, first.y - second.y)
                    else second.translate(second.x - first.x, second.y - first.y)
                player.playSound(Sound.SAW_RISE_AND_SPIN)
                if (!BrimhavenArena.traverse(player, listOf(AgilityStep(dest, 2, animation = 846)),
                        "You were hit by the saw blade!") { player.damage(player.hitpoints.level * 5 / 100 + 2) })
                    player.damage(player.hitpoints.level * 5 / 100 + 2)
            }
        }
    }
}
