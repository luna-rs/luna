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

import io.luna.game.model.item.Item
import kotlin.random.Random

/**
 * Original easy reward distribution: two to four rolls, each with a 1/97 rare chance.
 *
 * @author lare96
 */
object EasyTrailRewards {
    /**
     * An inclusive quantity range for one possible reward.
     *
     * @author lare96
     */
    data class Reward(val id: Int, val min: Int = 1, val max: Int = min)

    val normal = listOf(
        Reward(995, 50, 200), // coins
        Reward(334, 6, 10), // cert_trout
        Reward(330, 6, 10), // cert_salmon
        Reward(556, 30, 50), // airrune
        Reward(554, 30, 50), // firerune
        Reward(555, 30, 50), // waterrune
        Reward(557, 30, 50), // earthrune
        Reward(558, 30, 50), // mindrune
        Reward(559, 30, 50), // bodyrune
        Reward(563, 5, 10), // lawrune
        Reward(562, 5, 10), // chaosrune
        Reward(561, 5, 10), // naturerune
        Reward(1125, 1, 1), // black_platebody
        Reward(1297, 1, 1), // black_longsword
        Reward(1165, 1, 1), // black_full_helm
        Reward(1077, 1, 1), // black_platelegs
        Reward(1367, 1, 1), // black_battleaxe
        Reward(1361, 1, 1), // black_axe
        Reward(1217, 1, 1), // black_dagger
        Reward(1269, 1, 1), // steel_pickaxe
        Reward(1381, 1, 1), // staff_of_air
        Reward(1097, 1, 1), // studded_chaps
        Reward(1133, 1, 1), // studded_body
        Reward(849, 1, 1), // willow_shortbow
        Reward(1169, 1, 1), // coif
        Reward(1727, 1, 1), // amulet_of_magic
        Reward(847, 1, 1), // willow_longbow
    )
    val rare = listOf(
        Reward(2583), // black_platebody_trim
        Reward(2585), // black_platelegs_trim
        Reward(2587), // black_full_helm_trim
        Reward(2589), // black_kiteshield_trim
        Reward(2591), // black_platebody_gold
        Reward(2593), // black_platelegs_gold
        Reward(2595), // black_full_helm_gold
        Reward(2597), // black_kiteshield_gold
        Reward(2631), // highwaymanmask
        Reward(2633), // berret_blue
        Reward(2635), // berret_black
        Reward(2637), // berret_white
        Reward(2579), // boots_wizard
        Reward(7332), // black_heraldic_kiteshield1
        Reward(7338), // black_heraldic_kiteshield2
        Reward(7344), // black_heraldic_kiteshield3
        Reward(7350), // black_heraldic_kiteshield4
        Reward(7356), // black_heraldic_kiteshield5
        Reward(7394), // bluewizhat_trim_gold
        Reward(7396), // bluewizhat_trim
        Reward(7392), // wizards_robe_trim
        Reward(7390), // wizards_robe_trim_gold
    )

    fun roll(random: (Int) -> Int = { Random.nextInt(it) }): List<Item> =
        List(2 + random(3)) {
            val table = if (random(97) == 0) rare else normal
            val reward = table[random(table.size)]
            Item(reward.id, reward.min + random(reward.max - reward.min + 1))
        }
}
