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

import game.content.treasureTrails.EasyClues.Clue
import game.content.treasureTrails.EasyClues.Kind
import io.luna.game.model.Position

/**
 * All released medium and hard clues, including their keys, challenges and puzzles.
 *
 * @author lare96
 */
object TrailClues {
    val medium = listOf(
        Clue(2827, tier = TrailTier.MEDIUM, position = Position(3093, 3227, 0), casket = 2828, map = 7113),
        Clue(2801, tier = TrailTier.MEDIUM, text = "02 degrees 48 minutes North|22 degrees 30 minutes East", position = Position(3160, 3251, 0), casket = 2802, coordinate = true),
        Clue(2803, tier = TrailTier.MEDIUM, text = "01 degrees 35 minutes South|07 degrees 28 minutes East", position = Position(2679, 3110, 0), casket = 2804, coordinate = true),
        Clue(2805, tier = TrailTier.MEDIUM, text = "01 degrees 26 minutes North|08 degrees 01 minutes East", position = Position(2697, 3207, 0), casket = 2806, coordinate = true),
        Clue(2807, tier = TrailTier.MEDIUM, text = "06 degrees 31 minutes North|01 degrees 46 minutes West", position = Position(2383, 3370, 0), casket = 2808, coordinate = true),
        Clue(2809, tier = TrailTier.MEDIUM, text = "00 degrees 05 minutes South|01 degrees 13 minutes East", position = Position(2479, 3158, 0), casket = 2810, coordinate = true),
        Clue(2811, tier = TrailTier.MEDIUM, text = "09 degrees 33 minutes North|02 degrees 15 minutes East", position = Position(2512, 3467, 0), casket = 2812, coordinate = true),
        Clue(2813, tier = TrailTier.MEDIUM, text = "02 degrees 50 minutes North|06 degrees 20 minutes East", position = Position(2643, 3252, 0), casket = 2814, coordinate = true),
        Clue(2815, tier = TrailTier.MEDIUM, text = "04 degrees 13 minutes North|12 degrees 45 minutes East", position = Position(2848, 3296, 0), casket = 2816, coordinate = true),
        Clue(2817, tier = TrailTier.MEDIUM, text = "04 degrees 00 minutes South|12 degrees 46 minutes East", position = Position(2849, 3033, 0), casket = 2818, coordinate = true),
        Clue(2819, tier = TrailTier.MEDIUM, text = "00 degrees 31 minutes South|17 degrees 43 minutes East", position = Position(3007, 3144, 0), casket = 2820, coordinate = true),
        Clue(2821, tier = TrailTier.MEDIUM, text = "07 degrees 33 minutes North|15 degrees 00 minutes East", position = Position(2920, 3403, 0), casket = 2822, coordinate = true),
        Clue(2823, tier = TrailTier.MEDIUM, text = "00 degrees 30 minutes North|24 degrees 16 minutes East", position = Position(3217, 3177, 0), casket = 2824, coordinate = true),
        Clue(2825, tier = TrailTier.MEDIUM, text = "05 degrees 43 minutes North|23 degrees 05 minutes East", position = Position(3179, 3344, 0), casket = 2826, coordinate = true),
        Clue(2831, tier = TrailTier.MEDIUM, text = "You'll need to look for|a town with a central|fountain. Look for a locked|chest in the town's chapel.", position = Position(3256, 3487, 0), kind = Kind.CHEST_CLOSED, key = 2832, keyNpcs = setOf(202)),
        Clue(2833, tier = TrailTier.MEDIUM, text = "In a town where the guards|are armed with maces, search|the upstairs rooms of the|Public House.", position = Position(2574, 3326, 1), kind = Kind.DRAWER, key = 2834, keyNpcs = setOf(99)),
        Clue(2835, tier = TrailTier.MEDIUM, text = "In a town where thieves|steal from stalls, search for|some drawers in the upstairs|of a house near the bank.", position = Position(2611, 3324, 1), kind = Kind.DRAWER, key = 2836, keyNpcs = setOf(32)),
        Clue(2837, tier = TrailTier.MEDIUM, text = "In a town where everyone has|perfect vision, seek some|locked drawers in a house that|sits opposite a workshop.", position = Position(2709, 3478, 0), kind = Kind.DRAWER, key = 2838, keyNpcs = setOf(41, 951, 1017)),
        Clue(2839, tier = TrailTier.MEDIUM, text = "In a town where wizards are|known to gather, search|upstairs in a large|house to the north.", position = Position(2593, 3108, 1), kind = Kind.CHEST_CLOSED, key = 2840, keyNpcs = setOf(1, 2, 3, 16, 24, 351, 359, 663, 726, 727, 728, 729, 730, 1024, 1025, 1026, 1037, 2675, 3223, 3224, 3225, 1086)),
        Clue(2841, tier = TrailTier.MEDIUM, text = "Speak to Hazelmere.", npcId = 669, auxiliary = 2842, answer = 6859),
        Clue(2843, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||OK CO", npcId = 278, auxiliary = 2844, answer = 9),
        Clue(2845, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||EEK ZERO OP", npcId = 28, auxiliary = 2846, answer = 32),
        Clue(2847, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||EL OW", npcId = 550),
        Clue(2848, tier = TrailTier.MEDIUM, text = "Speak to Hajedy.", npcId = 510),
        Clue(2849, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||BAR BELL SEEK", npcId = 543, auxiliary = 2850, answer = 5),
        Clue(2851, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||ARE COL", npcId = 746, auxiliary = 2852, answer = 48),
        Clue(2853, tier = TrailTier.MEDIUM, text = "Speak to a referee.", npcId = 635, auxiliary = 2854, answer = 5096),
        Clue(2855, tier = TrailTier.MEDIUM, text = "Speak to Donovan, the|Family Handyman.", npcId = 806),
        Clue(2856, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||PEATY PERT", npcId = 659),
        Clue(2857, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||GOBLIN KERN", npcId = 469),
        Clue(2858, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||HALT US", npcId = 379),
    )
    val hard = listOf(
        Clue(2722, tier = TrailTier.HARD, map = 7221, position = Position(3309, 3503, 0), targetObjects = setOf(2620)),
        Clue(2723, tier = TrailTier.HARD, text = "22 degrees 35 minutes North|19 degrees 18 minutes East", position = Position(3058, 3884, 0), casket = 2724, coordinate = true, guardian = true),
        Clue(2725, tier = TrailTier.HARD, text = "25 degrees 03 minutes North|17 degrees 05 minutes East", position = Position(2987, 3963, 0), casket = 2726, coordinate = true, guardian = true),
        Clue(2727, tier = TrailTier.HARD, text = "24 degrees 56 minutes North|22 degrees 28 minutes East", position = Position(3159, 3959, 0), casket = 2728, coordinate = true, guardian = true),
        Clue(2729, tier = TrailTier.HARD, text = "25 degrees 03 minutes North|23 degrees 24 minutes East", position = Position(3189, 3963, 0), casket = 2730, coordinate = true, guardian = true),
        Clue(2731, tier = TrailTier.HARD, text = "22 degrees 45 minutes North|26 degrees 33 minutes East", position = Position(3290, 3889, 0), casket = 2732, coordinate = true, guardian = true),
        Clue(2733, tier = TrailTier.HARD, text = "20 degrees 05 minutes North|21 degrees 52 minutes East", position = Position(3140, 3804, 0), casket = 2734, coordinate = true, guardian = true),
        Clue(2735, tier = TrailTier.HARD, text = "20 degrees 33 minutes North|15 degrees 48 minutes East", position = Position(2946, 3819, 0), casket = 2736, coordinate = true, guardian = true),
        Clue(2737, tier = TrailTier.HARD, text = "21 degrees 24 minutes North|17 degrees 54 minutes East", position = Position(3013, 3846, 0), casket = 2738, coordinate = true, guardian = true),
        Clue(2739, tier = TrailTier.HARD, text = "24 degrees 58 minutes North|18 degrees 43 minutes East", position = Position(3039, 3960, 0), casket = 2740, coordinate = true, guardian = true),
        Clue(2741, tier = TrailTier.HARD, text = "19 degrees 43 minutes North|25 degrees 07 minutes East", position = Position(3244, 3792, 0), casket = 2742, coordinate = true, guardian = true),
        Clue(2743, tier = TrailTier.HARD, text = "16 degrees 07 minutes North|22 degrees 45 minutes East", position = Position(3168, 3677, 0), casket = 2744, coordinate = true, guardian = true),
        Clue(2745, tier = TrailTier.HARD, text = "16 degrees 43 minutes North|19 degrees 13 minutes East", position = Position(3055, 3696, 0), casket = 2746, coordinate = true, guardian = true),
        Clue(2747, tier = TrailTier.HARD, text = "19 degrees 00 minutes North|27 degrees 13 minutes East", position = Position(3311, 3769, 0), casket = 2748, coordinate = true, guardian = true),
        Clue(2773, tier = TrailTier.HARD, text = "You will need to under-cook|to solve this one.", position = Position(3219, 9617, 0), kind = Kind.EMPTY_CRATE),
        Clue(2774, tier = TrailTier.HARD, text = "Come to the evil ledge,|Yew know yew want to.|Try not to get stung.", position = Position(3089, 3468, 0), casket = 2775),
        Clue(2776, tier = TrailTier.HARD, text = "I am a token of the strongest love.|My middle is empty, I have no|beginning or end. My eye is red yet|I can fit like a glove. Go to the|place where money they lend, And|dig by the gate to be my friend.", position = Position(3191, 9825, 0), casket = 2777),
        Clue(2778, tier = TrailTier.HARD, text = "If a man carried my burden,|he would break his back.|I am not rich,|but leave silver in my track.|Speak to the keeper of my trail.", npcId = 558, casket = 2779),
        Clue(2780, tier = TrailTier.HARD, text = "Aggie I see.|Lonely and southern I feel.|I am neither inside nor outside|the house, yet no house would|be complete without me.|Your treasure waits beneath me!", position = Position(3085, 3255, 0), casket = 2781),
        Clue(2782, tier = TrailTier.HARD, text = "|My home is grey, and made of stone;|A castle with a search for a meal.|Hidden in some drawers I am,|across from a wooden wheel.", position = Position(3213, 3216, 1), kind = Kind.EMPTY_DRAWER),
        Clue(2783, tier = TrailTier.HARD, text = "The beasts to my east|snap claws and tails,|The rest to my west|can slide and eat fish.|The force to my north|will jump and they'll wail,|Come dig by my fire|and make a wish.", position = Position(2599, 3267, 0), casket = 2784),
        Clue(2785, tier = TrailTier.HARD, text = "Four blades I have|yet I draw no blood.|But I mash and turn|my victims to powder.|Search in my head,|search in my rafters,|where my blades are louder.", position = Position(3166, 3309, 2), kind = Kind.EMPTY_CRATES),
        Clue(2786, tier = TrailTier.HARD, text = "I lie lonely and forgotten|in mid wilderness,|where the dead rise from their beds.|Feel free to quarrel and wind me up,|and dig while you shoot their heads.", position = Position(3174, 3663, 0), casket = 2787),
        Clue(2788, tier = TrailTier.HARD, text = "46 is my number.|My body is burnt orange colour|and crawls with those with eight.|3 mouths I have, yet I cannot eat.|My blinking blue eye|hides my grave.", position = Position(3170, 3885, 0), casket = 2789),
        Clue(2790, tier = TrailTier.HARD, text = "My giant guardians below|the market streets would|be fans of rock and roll,|if only they could grab hold of it.|Dig near my green bubbles!", position = Position(3161, 9905, 0), casket = 2791),
        Clue(2792, tier = TrailTier.HARD, text = "Snah? I feel all confused,|like one of those cakes...", npcId = 0),
        Clue(2793, tier = TrailTier.HARD, text = "@or2@'A bag belt only?'|He asked his balding brothers.", npcId = 801),
        Clue(2794, tier = TrailTier.HARD, text = "The keeper of Melzars...|Spare? Skeleton? Anar?", npcId = 747, auxiliary = 2795, puzzle = true),
        Clue(2796, tier = TrailTier.HARD, text = "Surprising? I bet he is...", npcId = 883),
        Clue(2797, tier = TrailTier.HARD, text = "My name is like a tree,|yet it is spelt with a 'g'.|Come see the fur|which is right near me.", npcId = 783, auxiliary = 2798, puzzle = true),
        Clue(2799, tier = TrailTier.HARD, text = "Generally speaking,|his nose was very bent.", npcId = 296, auxiliary = 2800, puzzle = true),
    )
    val all = EasyClues.all + medium + hard
    val byId = all.associateBy { it.id }
    val byCasket = all.filter { it.casket != -1 }.associateBy { it.casket }
    val byAuxiliary = all.filter { it.auxiliary != -1 }.associateBy { it.auxiliary }
    val challenges = mapOf(
        2842 to "What is 19 to the power of 3?",
        2844 to "How many cannons does|Lumbridge Castle have?",
        2846 to "How many animals in total|are there in the zoo?",
        2850 to "I have 16 kebabs, I eat one|myself and then share the rest|equally between 3 friends.|How many do they have each?",
        2852 to "If x is 15 and y is 3|What is 3x + y?",
        2854 to "What is 57 x 89 + 23?",
    )
    val easyDrops = setOf(1, 2, 3, 4, 5, 6, 8, 12, 16, 17, 18, 100, 101, 102, 175, 186, 187, 199, 282, 298, 299, 489, 1084, 1085, 1087, 1088, 1089, 1090, 1612, 1648, 1649, 1650, 1651, 1652, 1653, 1654, 1655, 1656, 1657, 1769, 1770, 1771, 1772, 1773, 1774, 1775, 1776, 2274, 2275, 2276, 2277, 2278, 2279, 2280, 2281, 2678, 2679, 2680, 2681, 3222, 3223, 3224, 3225, 3226, 3227, 3246, 3247, 3248, 3249, 3250, 3251, 3252, 3253, 3255, 3256, 3257, 3258, 3259, 3260, 3261, 3262, 3264, 3265, 3266, 3267, 3406)
    val mediumDrops = setOf(9, 10, 20, 32, 113, 125, 145, 191, 202, 203, 204, 3228, 3229, 3230, 3231, 3232, 3233)
    val hardDrops = setOf(49, 50, 53, 54, 55, 83, 941, 1160, 1590, 1608, 1609)
    fun tierForNpc(id: Int): TrailTier? = when (id) {
        in easyDrops -> TrailTier.EASY
        in mediumDrops -> TrailTier.MEDIUM
        in hardDrops -> TrailTier.HARD
        else -> null
    }
}
