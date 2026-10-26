/*
 * Adapted from LostCityRS/Content 377-wip, commit 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
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
package game.content.fishingTrawler

/**
 * Shared boat simulation, advanced once per five game ticks, matching Lost City's 377 controller.
 * The supplied random function takes an exclusive upper bound.
 * Leak probabilities follow the source's reconstruction and are not verified historic rates.
 *
 * @author lare96
 */
class TrawlerVoyage(private val random: (Int) -> Int) {
    private val leaks = BooleanArray(16)
    private val repaired = BooleanArray(16)
    var cycles = 0
        private set
    var water = 0
        private set
    var flooded = false
        private set
    var netRipped = false
        private set
    var catch = 0
        private set
    var finished = false
        private set
    var sunk = false
        private set

    fun leaking(index: Int): Boolean = index in leaks.indices && leaks[index]
    fun repaired(index: Int): Boolean = index in repaired.indices && repaired[index]

    fun tick(players: Int) {
        if (finished) return
        if (players <= 0) { finished = true; return }
        cycles++
        if (cycles >= 200) { finished = true; return }
        water += leaks.count { it }
        val maxLeaks = minOf(players * 3L + 1, 9L).toInt()
        val chance = maxOf(32 / players, 2)
        var count = leaks.count { it }
        for (index in leaks.indices) {
            if (count >= maxLeaks) break
            if (!leaks[index] && random(chance) == 0) { leaks[index] = true; count++ }
        }
        if (!flooded && water >= 30) flooded = true
        else if (flooded && water < 1) flooded = false
        if (water >= 100) { sunk = true; finished = true; return }
        if (random(16) == 0) netRipped = true
        if (!netRipped) repeat(players) { if (random(10) == 0) catch++ }
    }

    fun repairHull(index: Int): Boolean {
        if (finished || !leaking(index)) return false
        leaks[index] = false
        repaired[index] = true
        return true
    }

    fun repairNet(): Boolean {
        if (finished || !netRipped) return false
        netRipped = false
        return true
    }

    fun bail(): Boolean {
        if (finished || !flooded || water <= 0) return false
        water--
        return true
    }
}
