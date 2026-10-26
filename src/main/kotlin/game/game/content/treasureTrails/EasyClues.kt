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

import io.luna.game.model.Position

/**
 * The 39 released easy clues, target categories and drop sources from Lost City's 377 content.
 *
 * @author lare96
 */
object EasyClues {
    /**
     * The native object category whose interaction solves a clue.
     *
     * @author lare96
     */
    enum class Kind { CHEST_CLOSED, EMPTY_CRATE, EMPTY_CRATES, EMPTY_BOXES, EMPTY_DRAWER, DRAWER, FALADOR_CRATES }

    /**
     * A clue's display and native target. Maps turn into caskets before advancing the trail.
     *
     * @author lare96
     */
    data class Clue(val id: Int, val text: String = "", val npcId: Int = -1,
                    val position: Position? = null, val kind: Kind? = null,
                    val map: Int = -1, val casket: Int = -1,
                    val tier: TrailTier = TrailTier.EASY, val coordinate: Boolean = false,
                    val guardian: Boolean = false, val key: Int = -1,
                    val keyNpcs: Set<Int> = emptySet(), val auxiliary: Int = -1,
                    val answer: Int = -1, val puzzle: Boolean = false,
                    val targetObjects: Set<Int> = emptySet())

    val all = listOf(
        Clue(2713, position = Position(3177, 3360, 0), map = 6994, casket = 2714), // map001
        Clue(2716, position = Position(3290, 3374, 0), map = 7045, casket = 2717), // map002
        Clue(2719, position = Position(3043, 3398, 0), map = 7271, casket = 2720), // map003
        Clue(2677, text = "Search the chest in the|Duke of Lumbridge's bedroom.", position = Position(3209, 3218, 1), kind = Kind.CHEST_CLOSED), // simple001
        Clue(2678, text = "Search the crate in the left-|hand tower of Lumbridge Castle.", position = Position(3228, 3212, 1), kind = Kind.EMPTY_CRATE), // simple002
        Clue(2679, text = "Search the boxes in the goblin|house near Lumbridge.", position = Position(3245, 3245, 0), kind = Kind.EMPTY_BOXES), // simple003
        Clue(2680, text = "Search the chests upstairs in|Al Kharid Palace.", position = Position(3301, 3169, 1), kind = Kind.CHEST_CLOSED), // simple004
        Clue(2681, text = "Speak to Hans to|solve the clue.", npcId = 0), // simple005
        Clue(2682, text = "Search the crates in the|most north-western house in Al Kharid.", position = Position(3289, 3202, 0), kind = Kind.EMPTY_CRATE), // simple006
        Clue(2683, text = "Talk to Zeke in Al Kharid.", npcId = 541), // simple007
        Clue(2684, text = "Speak to the tanner|in Al Kharid.", npcId = 2824), // simple008
        Clue(2685, text = "Search the boxes in|the house near the|south entrance to Varrock.", position = Position(3203, 3384, 0), kind = Kind.EMPTY_BOXES), // simple009
        Clue(2686, text = "Speak to the bartender of the|Blue Moon Inn in Varrock.", npcId = 733), // simple010
        Clue(2687, text = "Search the drawers, upstairs|in the bank to|the East of Varrock.", position = Position(3250, 3420, 1), kind = Kind.EMPTY_DRAWER), // simple011
        Clue(2688, text = "Search the crates|in Horvik's armoury.", position = Position(3228, 3433, 0), kind = Kind.EMPTY_CRATE), // simple012
        Clue(2689, text = "Search the drawers in|one of Gertrude's bedrooms.", position = Position(3156, 3406, 0), kind = Kind.EMPTY_DRAWER), // simple013
        Clue(2690, text = "Search the boxes in the|Barbarian Village helmet shop.", position = Position(3073, 3430, 0), kind = Kind.EMPTY_BOXES), // simple014
        Clue(2691, text = "Search the drawers upstairs|in Falador's shield shop.", position = Position(2971, 3386, 1), kind = Kind.EMPTY_DRAWER), // simple015
        Clue(2692, text = "Search the crates of|Falador's general store.", position = Position(2955, 3390, 0), kind = Kind.FALADOR_CRATES), // simple016
        Clue(2693, text = "Talk to the Squire in the|White Knights' castle in Falador.", npcId = 606), // simple017
        Clue(2694, text = "Search the drawers in|Falador's chain mail shop.", position = Position(2969, 3311, 0), kind = Kind.EMPTY_DRAWER), // simple018
        Clue(2695, text = "Search the crates in the|Port Sarim Fishing shop.", position = Position(3012, 3222, 0), kind = Kind.EMPTY_CRATE), // simple019
        Clue(2696, text = "Talk to the bartender of the|Rusty Anchor in Port Sarim.", npcId = 734), // simple020
        Clue(2697, text = "Speak to Ned in|Draynor Village.", npcId = 918), // simple021
        Clue(2698, text = "Speak to Doric, who|lives north of Falador.", npcId = 284), // simple022
        Clue(2699, text = "Speak to Gaius in Taverley.", npcId = 586), // simple023
        Clue(2700, text = "Search the drawers in|Catherby's Archery shop.", position = Position(2825, 3442, 0), kind = Kind.EMPTY_DRAWER), // simple024
        Clue(2701, text = "Speak to Arhein in Catherby.", npcId = 563), // simple025
        Clue(2702, text = "Speak to Sir Kay|in Camelot Castle.", npcId = 241), // simple026
        Clue(2703, text = "Search the chest in the left-|hand tower of Camelot Castle.", position = Position(2748, 3495, 2), kind = Kind.CHEST_CLOSED), // simple027
        Clue(2704, text = "Search the crates in the|guard house of the northern|gate of East Ardougne.", position = Position(2645, 3338, 0), kind = Kind.EMPTY_CRATE), // simple028
        Clue(2705, text = "Search the boxes just|outside the Armour shop|in East Ardougne.", position = Position(2654, 3299, 0), kind = Kind.EMPTY_BOXES), // simple029
        Clue(2706, text = "Search the crates in East|Ardougne's general store.", position = Position(2615, 3291, 0), kind = Kind.EMPTY_CRATE), // simple030
        Clue(2707, text = "Search the crates in the shed|just north of East Ardougne.", position = Position(2617, 3347, 0), kind = Kind.EMPTY_CRATES), // simple031
        Clue(2708, text = "Search the drawers|above Varrock's shops.", position = Position(3206, 3419, 1), kind = Kind.EMPTY_DRAWER), // vague001
        Clue(2709, text = "Search the crates near|a cart in Varrock.", position = Position(3226, 3452, 0), kind = Kind.EMPTY_CRATE), // vague002
        Clue(2710, text = "Search the drawers found upstairs|in East Ardougne's houses.", position = Position(2574, 3326, 1), kind = Kind.DRAWER), // vague003
        Clue(2711, text = "A crate found in the|tower of a church is|your next location.", position = Position(2612, 3304, 1), kind = Kind.EMPTY_CRATE), // vague004
        Clue(2712, text = "Look in the ground floor|crates of houses in Falador.", position = Position(3029, 3355, 0), kind = Kind.FALADOR_CRATES), // vague005
    )
    val byId = all.associateBy { it.id }
    val byCasket = all.filter { it.casket != -1 }.associateBy { it.casket }

    val closedChests = setOf(375, 376)
    val openChests = setOf(378, 379)
    val emptyCrate = setOf(354, 356, 357, 358, 366, 3813, 3814, 3815, 5355, 5356, 5357, 7513, 8912, 9381, 9382, 9533, 9534, 9535, 10279, 10280, 10281, 10282, 10298, 10380, 10381, 11485, 11486, 11488, 11599, 11970, 12104, 12105, 12874, 12877, 12879, 12961, 12977)
    val emptyCrates = setOf(355, 12963, 12978)
    val emptyBoxes = setOf(359, 360, 361, 3685, 3686, 3687, 9536, 11600)
    val emptyDrawer = setOf(349, 351, 353, 3191, 5619)
    val drawer = setOf(348, 350, 352, 3190, 5618)
    val drawerOpen = mapOf(348 to 349, 350 to 351, 352 to 353, 3190 to 3191, 5618 to 3191)

    val trailItems = setOf(
        2677, 2678, 2679, 2680, 2681, 2682, 2683, 2684, 2685, 2686, 2687, 2688, 2689, 2690, 2691,
        2692, 2693, 2694, 2695, 2696, 2697, 2698, 2699, 2700, 2701, 2702, 2703, 2704, 2705, 2706,
        2707, 2708, 2709, 2710, 2711, 2712, 2713, 2714, 2716, 2717, 2719, 2720, 2722, 2723, 2724,
        2725, 2726, 2727, 2728, 2729, 2730, 2731, 2732, 2733, 2734, 2735, 2736, 2737, 2738, 2739,
        2740, 2741, 2742, 2743, 2744, 2745, 2746, 2747, 2748, 2773, 2774, 2775, 2776, 2777, 2778,
        2779, 2780, 2781, 2782, 2783, 2784, 2785, 2786, 2787, 2788, 2789, 2790, 2791, 2792, 2793,
        2794, 2796, 2797, 2799, 2801, 2802, 2803, 2804, 2805, 2806, 2807, 2808, 2809, 2810, 2811,
        2812, 2813, 2814, 2815, 2816, 2817, 2818, 2819, 2820, 2821, 2822, 2823, 2824, 2825, 2826,
        2827, 2828, 2829, 2830, 2831, 2833, 2835, 2837, 2839, 2841, 2843, 2845, 2847, 2848, 2849,
        2851, 2853, 2855, 2856, 2857, 2858, 3519, 3527, 3529, 3531, 3533, 3535, 3537, 3539, 3541,
        3543, 3545, 3547, 3549, 3551, 3553, 3555, 3557, 3559, 3561, 3563, 3583, 3585, 3587, 3589,
        3591, 3593, 3595, 3597, 3600, 3603, 7237, 7256, 7257, 7259, 7261, 7263, 7265, 7267, 7287,
        7289, 7291, 7293, 7295, 7306, 7308, 7310, 7312, 7314, 7316, 7318,
    )

}
