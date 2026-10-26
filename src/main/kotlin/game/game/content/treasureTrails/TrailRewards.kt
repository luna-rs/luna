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

import game.content.treasureTrails.EasyTrailRewards.Reward
import io.luna.game.model.item.Item
import kotlin.random.Random

/**
 * Reward tables for all three tiers, preserving the original roll and quantity distributions.
 *
 * @author lare96
 */
object TrailRewards {
    val mediumNormal = listOf(
        Reward(995, 200, 1000), // coins
        Reward(380, 8, 12), // cert_lobster
        Reward(374, 8, 12), // cert_swordfish
        Reward(560, 10, 20), // deathrune
        Reward(563, 10, 20), // lawrune
        Reward(561, 10, 20), // naturerune
        Reward(562, 10, 20), // chaosrune
        Reward(554, 50, 100), // firerune
        Reward(557, 50, 100), // earthrune
        Reward(555, 50, 100), // waterrune
        Reward(558, 50, 100), // mindrune
        Reward(556, 50, 100), // airrune
        Reward(1161, 1, 1), // adamant_full_helm
        Reward(1123, 1, 1), // adamant_platebody
        Reward(1073, 1, 1), // adamant_platelegs
        Reward(1301, 1, 1), // adamant_longsword
        Reward(1371, 1, 1), // adamant_battleaxe
        Reward(1357, 1, 1), // adamant_axe
        Reward(1211, 1, 1), // adamant_dagger
        Reward(1271, 1, 1), // adamant_pickaxe
        Reward(1135, 1, 1), // dragonhide_body
        Reward(1099, 1, 1), // dragonhide_chaps
        Reward(857, 1, 1), // yew_shortbow
        Reward(1393, 1, 1), // fire_battlestaff
        Reward(855, 1, 1), // yew_longbow
        Reward(1731, 1, 1), // amulet_of_power
    )
    val mediumRare = listOf(
        Reward(2577, 1, 1), // boots_ranger
        Reward(2605, 1, 1), // adamant_full_helm_trim
        Reward(2599, 1, 1), // adamant_platebody_trim
        Reward(2601, 1, 1), // adamant_platelegs_trim
        Reward(2603, 1, 1), // adamant_kiteshield_trim
        Reward(2607, 1, 1), // adamant_platebody_gold
        Reward(2609, 1, 1), // adamant_platelegs_gold
        Reward(2613, 1, 1), // adamant_full_helm_gold
        Reward(2611, 1, 1), // adamant_kiteshield_gold
        Reward(2645, 1, 1), // headband_red
        Reward(2647, 1, 1), // headband_black
        Reward(2649, 1, 1), // headband_brown
        Reward(2579, 1, 1), // boots_wizard
        Reward(7334, 1, 1), // adamant_heraldic_kiteshield1
        Reward(7340, 1, 1), // adamant_heraldic_kiteshield2
        Reward(7346, 1, 1), // adamant_heraldic_kiteshield3
        Reward(7352, 1, 1), // adamant_heraldic_kiteshield4
        Reward(7358, 1, 1), // adamant_heraldic_kiteshield5
    )
    val hardNormal = listOf(
        Reward(995, 1000, 5000), // coins
        Reward(380, 12, 15), // cert_lobster
        Reward(386, 12, 15), // cert_shark
        Reward(561, 30, 50), // naturerune
        Reward(563, 30, 50), // lawrune
        Reward(565, 20, 30), // bloodrune
        Reward(1163, 1, 1), // rune_full_helm
        Reward(1127, 1, 1), // rune_platebody
        Reward(1079, 1, 1), // rune_platelegs
        Reward(1093, 1, 1), // rune_plateskirt
        Reward(1201, 1, 1), // rune_kiteshield
        Reward(1303, 1, 1), // rune_longsword
        Reward(1213, 1, 1), // rune_dagger
        Reward(1373, 1, 1), // rune_battleaxe
        Reward(1359, 1, 1), // rune_axe
        Reward(1275, 1, 1), // rune_pickaxe
        Reward(2503, 1, 1), // black_dragonhide_body
        Reward(2497, 1, 1), // black_dragonhide_chaps
        Reward(861, 1, 1), // magic_shortbow
        Reward(859, 1, 1), // magic_longbow
    )
    val hardRare = listOf(
        Reward(2581, 1, 1), // robinhoodhat
        Reward(2627, 1, 1), // rune_full_helm_trim
        Reward(2623, 1, 1), // rune_platebody_trim
        Reward(2625, 1, 1), // rune_platelegs_trim
        Reward(2629, 1, 1), // rune_kiteshield_trim
        Reward(2619, 1, 1), // rune_full_helm_gold
        Reward(2615, 1, 1), // rune_platebody_gold
        Reward(2617, 1, 1), // rune_platelegs_gold
        Reward(2621, 1, 1), // rune_kiteshield_gold
        Reward(2651, 1, 1), // piratehat
        Reward(2639, 1, 1), // cavalier_brown
        Reward(2641, 1, 1), // cavalier_dark
        Reward(2643, 1, 1), // cavalier_black
        Reward(2657, 1, 1), // rune_full_helm_zamorak
        Reward(2653, 1, 1), // rune_platebody_zamorak
        Reward(2655, 1, 1), // rune_platelegs_zamorak
        Reward(2659, 1, 1), // rune_kiteshield_zamorak
        Reward(2673, 1, 1), // rune_full_helm_guthix
        Reward(2669, 1, 1), // rune_platebody_guthix
        Reward(2671, 1, 1), // rune_platelegs_guthix
        Reward(2675, 1, 1), // rune_kiteshield_guthix
        Reward(2665, 1, 1), // rune_full_helm_saradomin
        Reward(2661, 1, 1), // rune_platebody_saradomin
        Reward(2663, 1, 1), // rune_platelegs_saradomin
        Reward(2667, 1, 1), // rune_kiteshield_saradomin
        Reward(7400, 1, 1), // enchanted_hat
        Reward(7399, 1, 1), // enchanted_robetop
        Reward(7398, 1, 1), // enchanted_robelegs
        Reward(7336, 1, 1), // rune_heraldic_kiteshield1
        Reward(7342, 1, 1), // rune_heraldic_kiteshield2
        Reward(7348, 1, 1), // rune_heraldic_kiteshield3
        Reward(7354, 1, 1), // rune_heraldic_kiteshield4
        Reward(7360, 1, 1), // rune_heraldic_kiteshield5
    )

    fun normal(tier: TrailTier): List<Reward> = when (tier) {
        TrailTier.EASY -> EasyTrailRewards.normal
        TrailTier.MEDIUM -> mediumNormal
        TrailTier.HARD -> hardNormal
    }

    fun rare(tier: TrailTier): List<Reward> = when (tier) {
        TrailTier.EASY -> EasyTrailRewards.rare
        TrailTier.MEDIUM -> mediumRare
        TrailTier.HARD -> hardRare
    }

    fun roll(tier: TrailTier, random: (Int) -> Int = { Random.nextInt(it) }): List<Item> =
        List(tier.rewardRolls + random(3)) {
            val table = if (random(tier.rareChance) == 0) rare(tier) else normal(tier)
            val reward = table[random(table.size)]
            Item(reward.id, reward.min + random(reward.max - reward.min + 1))
        }
}
