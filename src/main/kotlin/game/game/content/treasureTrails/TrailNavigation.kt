/*
 * Rules and interface identifiers adapted from LostCityRS/Content, commit 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
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
package game.content.treasureTrails

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.net.msg.out.WidgetAnimationMessageWriter
import io.luna.net.msg.out.WidgetPositionMessageWriter
import kotlin.math.abs
import kotlin.random.Random

/**
 * The professor, Murphy and Brother Kojo supply the equipment used by coordinate clues.
 * Sextant adjustments use native controls and retain the observatory's original coordinate origin.
 *
 * @author lare96
 */
object TrailNavigation {
    val equipment = listOf(2574, 2575, 2576)
    val suppliers = setOf(488, 463, 223)
    var Player.chartProgress by Attr.int().persist("trail_chart_progress")

    /**
     * A transient sextant reading. The frame and needle bounds prevent impossible adjustments.
     *
     * @author lare96
     */
    class SextantInterface(random: (Int) -> Int) : StandardInterface(6946) {
        var frame = random(15)
        var needle = frame + 27 + random(21)
        var sun = 2 * (needle - 45) - 4 * (frame - 8)

        fun adjust(button: Int): Boolean {
            when (button) {
                6952 -> if (frame < 14) { frame++; needle++; sun -= 2 } else return false
                6953 -> if (frame > 0) { frame--; needle--; sun += 2 } else return false
                6954 -> if (needle < frame + 60) { needle++; sun += 2 } else return false
                6955 -> if (needle > frame + 15) { needle--; sun -= 2 } else return false
                else -> return false
            }
            return true
        }
    }

    fun hasEquipment(player: Player): Boolean = equipment.all { player.inventory.contains(it) }

    fun talk(player: Player, npc: Npc): Boolean {
        if (npc.id !in suppliers || !player.isAlive || player.isLocked) return false
        val started = player.chartProgress
        val hasCoordinate = TrailClues.all.any { it.coordinate && player.inventory.contains(it.id) }
        if (started == 0 && !hasCoordinate) return false
        val item = when (npc.id) {
            488 -> {
                if (started == 0) {
                    player.chartProgress = 1
                    player.overlays.closeWindows()
                    player.sendMessage("Ask Murphy at Port Khazard for a sextant, then Brother Kojo for a watch.")
                    player.sendMessage("Return to me for your navigation chart.")
                    return true
                }
                if (started < 3) {
                    player.sendMessage("Collect your sextant from Murphy and your watch from Brother Kojo first.")
                    return true
                }
                2576
            }
            463 -> if (started >= 1) 2574 else return false
            223 -> if (started >= 2) 2575 else return false
            else -> return false
        }
        if (player.inventory.contains(item) || player.bank.contains(item)) return false
        player.overlays.closeWindows()
        if (!player.inventory.hasSpaceFor(Item(item))) {
            player.sendMessage("You need a free inventory slot.")
            return true
        }
        player.inventory.add(Item(item))
        player.chartProgress = maxOf(started, when (item) { 2574 -> 2; 2575 -> 3; else -> 4 })
        player.sendMessage("You receive a ${when (item) { 2574 -> "sextant"; 2575 -> "watch"; else -> "navigation chart" }}.")
        return true
    }

    fun open(player: Player, random: (Int) -> Int = { Random.nextInt(it) }): Boolean {
        if (!player.isAlive || player.isLocked || !player.inventory.contains(2574)) return false
        val overlay = SextantInterface(random)
        player.overlays.open(overlay)
        display(player, overlay)
        return true
    }

    fun usable(position: Position): Boolean {
        if (position.y > 6400) return false
        if (position.y in 4672..4735 && (position.x in 2624..2815 || position.x in 2368..2431)) return false
        if (position.z == 2 && position.y in 4672..4735 && position.x in 2880..2943) return false
        return !(position.z == 0 && position.y in 4800..4863 && position.x in 2048..2879)
    }

    fun coordinates(position: Position): List<String> {
        fun line(displacement: Int, positive: String, negative: String): String {
            val minutes = displacement * 1875 / 1000
            return "${abs(minutes) / 60} degrees, ${abs(minutes) % 60} minutes ${if (minutes < 0) negative else positive}"
        }
        return listOf(line(position.y - 3161, "North", "South"), line(position.x - 2440, "East", "West"))
    }

    fun button(player: Player, button: Int): Boolean {
        val overlay = player.overlays.getOverlay(SextantInterface::class.java) ?: return false
        if (!player.isAlive || player.isLocked || !player.inventory.contains(2574)) return false
        if (button == 6959) {
            when {
                !hasEquipment(player) -> player.sendMessage("You need a watch and navigation chart to work out your location.")
                !usable(player.position) -> player.sendMessage("The sextant doesn't work here.")
                overlay.frame != 8 -> player.sendMessage("You need to centre the horizon in the eyepiece.")
                overlay.sun != 0 -> player.sendMessage("You need to centre the sun in the eyepiece.")
                else -> {
                    player.overlays.closeWindows()
                    coordinates(player.position).forEach(player::sendMessage)
                }
            }
            return true
        }
        if (!overlay.adjust(button)) return false
        display(player, overlay)
        return true
    }

    private fun display(player: Player, overlay: SextantInterface) {
        player.queue(WidgetPositionMessageWriter(6948, 0, (overlay.frame - 8) * 7))
        player.queue(WidgetPositionMessageWriter(6949, 0, if (player.position.y > 6400) 1000 else overlay.sun))
        player.queue(WidgetAnimationMessageWriter(6956, 934 + overlay.frame))
        player.queue(WidgetAnimationMessageWriter(6958, 934 + overlay.frame))
        player.queue(WidgetAnimationMessageWriter(6957, 934 + overlay.needle.coerceAtMost(72)))
    }
}
