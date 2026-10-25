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
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.net.msg.out.WidgetItemsMessageWriter
import kotlin.math.abs
import kotlin.random.Random

/**
 * A persistent 5-by-5 puzzle. Shuffling through legal moves guarantees a solvable board.
 *
 * @author lare96
 */
object TrailPuzzle {
    var Player.puzzleBox by Attr.int().persist("trail_puzzle_box")
    var Player.puzzleTiles by Attr.string().persist("trail_puzzle_tiles")

    /**
     * Identifies the active puzzle interface so unrelated widget packets cannot move pieces.
     *
     * @author lare96
     */
    class PuzzleInterface(val box: Int) : StandardInterface(6976)

    fun clear(player: Player) {
        player.puzzleBox = 0
        player.puzzleTiles = ""
    }

    fun adjacent(a: Int, b: Int): Boolean = a in 0..24 && b in 0..24 &&
        abs(a / 5 - b / 5) + abs(a % 5 - b % 5) == 1

    fun shuffle(random: (Int) -> Int = { Random.nextInt(it) }): List<Int> {
        val tiles = ((1..24).toList() + 0).toMutableList()
        var blank = 24
        repeat(101) {
            val choices = (0..24).filter { adjacent(it, blank) }
            val next = choices[random(choices.size)]
            tiles[blank] = tiles[next]
            tiles[next] = 0
            blank = next
        }
        return tiles
    }

    fun start(player: Player, box: Int, random: (Int) -> Int = { Random.nextInt(it) }) {
        player.puzzleBox = box
        player.puzzleTiles = shuffle(random).joinToString(",")
    }

    fun tiles(player: Player): List<Int>? {
        val tiles = player.puzzleTiles.split(',').map { it.toIntOrNull() ?: return null }
        return tiles.takeIf { it.size == 25 && it.toSet() == (0..24).toSet() }
    }

    private fun owns(player: Player, box: Int): Boolean {
        val clue = TrailClues.byAuxiliary[box] ?: return false
        return clue.puzzle && player.inventory.contains(box) && player.inventory.contains(clue.id)
    }

    fun open(player: Player, box: Int): Boolean {
        if (!player.isAlive || player.isLocked || !owns(player, box)) return false
        if (player.puzzleBox != box || tiles(player) == null) start(player, box)
        player.overlays.open(PuzzleInterface(box))
        display(player)
        player.queue(WidgetItemsMessageWriter(6985, ((1..24).map { Item(2748 + it) } + null).toTypedArray()))
        return true
    }

    fun solved(player: Player, box: Int): Boolean = owns(player, box) && player.puzzleBox == box &&
        tiles(player) == (1..24).toList() + 0

    fun validClick(player: Player, index: Int, itemId: Int): Boolean {
        val overlay = player.overlays.getOverlay(PuzzleInterface::class.java) ?: return false
        if (!player.isAlive || player.isLocked || overlay.box != player.puzzleBox || !owns(player, overlay.box)) return false
        val tiles = tiles(player) ?: return false
        return index in tiles.indices && tiles[index] != 0 && itemId == 2748 + tiles[index]
    }

    fun move(player: Player, index: Int, itemId: Int): Boolean {
        if (!validClick(player, index, itemId)) return false
        val tiles = tiles(player)!!.toMutableList()
        val blank = tiles.indexOf(0)
        if (!adjacent(index, blank)) return false
        tiles[blank] = tiles[index]
        tiles[index] = 0
        player.puzzleTiles = tiles.joinToString(",")
        display(player)
        return true
    }

    private fun display(player: Player) {
        val items = tiles(player)?.map { if (it == 0) null else Item(2748 + it) } ?: return
        player.queue(WidgetItemsMessageWriter(6980, items.toTypedArray()))
    }
}
