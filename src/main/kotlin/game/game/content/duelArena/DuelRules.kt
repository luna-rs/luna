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
package game.content.duelArena

/**
 * The original Arena's selectable rules and native interface buttons.
 *
 * @author lare96
 */
enum class DuelRules(val bit: Int, val button: Int, val label: String) {
    NO_FORFEIT(0, 6721, "No forfeiting"), NO_MOVEMENT(1, 6722, "No movement"),
    NO_RANGED(4, 6725, "No ranged"), NO_MELEE(5, 6726, "No melee"),
    NO_MAGIC(6, 6727, "No magic"), NO_POTIONS(7, 6728, "No drinks"),
    NO_FOOD(8, 6729, "No food"), NO_PRAYER(9, 6730, "No prayer"),
    OBSTACLES(10, 6732, "Obstacles"), FLOWERS(12, 670, "Flowers only"),
    NO_SPECIAL(13, 7816, "No special attacks");

    fun enabled(mask: Int): Boolean = mask and (1 shl bit) != 0

    companion object {
        fun valid(mask: Int): Boolean = !(NO_MELEE.enabled(mask) &&
            (NO_FORFEIT.enabled(mask) || NO_MOVEMENT.enabled(mask) || FLOWERS.enabled(mask) ||
                NO_RANGED.enabled(mask) && NO_MAGIC.enabled(mask)))
    }
}
