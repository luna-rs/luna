/*
 * Random-event behavior adapted from LostCityRS/Content (377-wip) and LostCityRS/Engine-TS,
 * engine commit 1d25566cb53e7af1b1cb18ade8af996316c19614.
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
package game.content.randomEvents

import kotlin.random.Random

/**
 * Lost City's AFK-event readiness flag and two overlapping 21-tile activity areas.
 * Readiness is rolled every 500 world ticks and consumed once by an eligible content trigger.
 * Remaining within the tracked areas for 1,000 ticks changes the chance from 1/24 to 1/12.
 * Plane changes do not reset the areas. A teleport outside both areas replaces both anchors.
 *
 * @author lare96
 */
class RandomEventClock {
    /**
     * Lower-left coordinates of the current and previous activity areas, preserved across logout.
     */
    var currentX = 0
    var currentY = 0
    var previousX = 0
    var previousY = 0

    /**
     * Time within the existing areas, capped at Lost City's stationary threshold.
     */
    var stationaryTicks = 0

    /**
     * A rolled opportunity; this is runtime state rather than a saved encounter entitlement.
     */
    @Transient
    var ready = false
        private set

    /**
     * Updates activity areas after movement, matching Player.updateAfkZones in Lost City's engine.
     */
    fun observe(x: Int, y: Int, teleported: Boolean) {
        stationaryTicks = (stationaryTicks + 1).coerceAtMost(1000)
        if (inside(x, y, currentX, currentY) || inside(x, y, previousX, previousY)) return
        previousX = if (teleported) x - 10 else currentX
        previousY = if (teleported) y - 10 else currentY
        currentX = x - 10
        currentY = y - 10
        stationaryTicks = 0
    }

    /**
     * Replaces the readiness flag at a world polling boundary, including when an old roll was unused.
     * The random supplier permits deterministic checks without changing production probabilities.
     */
    fun poll(random: (Int) -> Int = { Random.nextInt(it) }) {
        ready = random(if (stationaryTicks >= 1000) 12 else 24) == 0
    }

    /**
     * Implements the read-and-clear behavior of the AFK_EVENT opcode. Eligibility is checked first.
     */
    fun consume(): Boolean = ready.also { ready = false }

    /**
     * Tests a one-tile player against a source area, without using the player's plane.
     */
    private fun inside(x: Int, y: Int, originX: Int, originY: Int): Boolean =
        x in originX..originX + 20 && y in originY..originY + 20
}
