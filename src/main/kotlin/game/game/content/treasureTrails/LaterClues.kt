package game.content.treasureTrails

import game.content.treasureTrails.EasyClues.Clue
import game.content.treasureTrails.EasyClues.Kind
import io.luna.game.model.Position

/**
 * The clues released on 26 October 2004 and 20 February 2006 that the original tables leave out. Map interfaces,
 * caskets, keys, puzzle boxes and challenge scrolls are the 377 cache's own.
 */
object LaterClues {
    val easy = listOf(
        Clue(3490, tier = TrailTier.EASY, text = "Search the drawers of houses|in Burthorpe.", position = Position(2929, 3570, 0), kind = Kind.EMPTY_DRAWER), // easy_vague006
        Clue(3491, tier = TrailTier.EASY, text = "Search the crates in a house|in Yanille that has a piano.", position = Position(2598, 3105, 0), targetObjects = setOf(357)), // easy_vague007
        Clue(3492, tier = TrailTier.EASY, text = "Search the drawers in the|ground floor of a shop in|Yanille.", position = Position(2570, 3085, 0), kind = Kind.EMPTY_DRAWER), // easy_vague008
        Clue(3493, tier = TrailTier.EASY, text = "Search the chests in the|Dwarven Mine.", position = Position(3000, 9798, 0), kind = Kind.CHEST_CLOSED), // easy_vague009
        Clue(3494, tier = TrailTier.EASY, text = "Search through some drawers in|the upstairs of a house in|Rimmington.", position = Position(2970, 3214, 1), kind = Kind.EMPTY_DRAWER), // easy_vague010
        Clue(3495, tier = TrailTier.EASY, text = "Search chests found in the|upstairs of shops in Port|Sarim.", position = Position(3016, 3205, 1), kind = Kind.CHEST_CLOSED), // easy_vague011
        Clue(3496, tier = TrailTier.EASY, text = "One of the sailors in Port|Sarim is your next|destination.", npcId = 376), // easy_vague012
        Clue(3497, tier = TrailTier.EASY, text = "Search through chests found in|the upstairs of houses in|eastern Falador.", position = Position(3041, 3364, 1), kind = Kind.CHEST_CLOSED), // easy_vague013
        Clue(3498, tier = TrailTier.EASY, text = "Search the drawers downstairs|of houses in the eastern part|of Falador.", position = Position(3039, 3342, 0), kind = Kind.EMPTY_DRAWER), // easy_vague014
        Clue(3499, tier = TrailTier.EASY, text = "Search the boxes in a shop in|Taverley.", position = Position(2886, 3449, 0), targetObjects = setOf(360)), // easy_vague015
        Clue(3500, tier = TrailTier.EASY, text = "Search through some drawers|found in Taverley's houses.", position = Position(2894, 3418, 0), kind = Kind.EMPTY_DRAWER), // easy_vague016
        Clue(3501, tier = TrailTier.EASY, text = "Search the boxes in one of the|tents in Al Kharid.", position = Position(3308, 3206, 0), targetObjects = setOf(361)), // easy_vague017
        Clue(3502, tier = TrailTier.EASY, text = "Search the crates in Draynor|Manor.", position = Position(3106, 3369, 2), targetObjects = setOf(11485)), // easy_vague018
        Clue(3503, tier = TrailTier.EASY, text = "Search the tents in the|Imperial Guard camp in|Burthorpe for some boxes.", position = Position(2885, 3540, 0), targetObjects = setOf(3686)), // easy_vague019
        Clue(3504, tier = TrailTier.EASY, text = "Search the crate near the|southern general store in Port|Khazard.", position = Position(2660, 3149, 0), targetObjects = setOf(366)), // easy_vague020
        Clue(3506, tier = TrailTier.EASY, text = "Search for a crate in a|building in Hemenster.", position = Position(2636, 3453, 0), targetObjects = setOf(357)), // easy_vague022
        Clue(3507, tier = TrailTier.EASY, text = "Search the drawers in the|upstairs of a house in|Catherby.", position = Position(2809, 3451, 1), kind = Kind.EMPTY_DRAWER), // easy_vague023
        Clue(3508, tier = TrailTier.EASY, text = "Search upstairs in the houses|of Seers' Village for some|drawers.", position = Position(2716, 3471, 1), kind = Kind.EMPTY_DRAWER), // easy_vague024
        Clue(3509, tier = TrailTier.EASY, text = "Search for a crate on the|ground floor of a house in|Seers' Village.", position = Position(2699, 3470, 0), targetObjects = setOf(357)), // easy_vague025
        Clue(3510, tier = TrailTier.EASY, text = "Dig near some giant mushrooms,|behind the Grand Tree.", position = Position(2458, 3504, 0), casket = 3511), // easy_vague026
        Clue(3512, tier = TrailTier.EASY, text = "Search the drawers in a house|in Draynor Village.", position = Position(3097, 3277, 0), kind = Kind.EMPTY_DRAWER), // easy_vague027
        Clue(3513, tier = TrailTier.EASY, text = "Speak to the staff of Sinclair|mansion.", npcId = 809), // easy_vague028
        Clue(3514, tier = TrailTier.EASY, text = "Someone watching the fights at|the Duel Arena is your next|destination.", npcId = 969), // easy_vague029
        Clue(3515, tier = TrailTier.EASY, text = "Search for a crate in Varrock|Castle.", position = Position(3224, 3492, 0), targetObjects = setOf(356)), // easy_vague030
        Clue(3516, tier = TrailTier.EASY, map = 9108, position = Position(2612, 3482, 0), casket = 3517), // easy_map004
        Clue(3518, tier = TrailTier.EASY, map = 9275, position = Position(3109, 3153, 0), casket = 3519), // easy_map005
        Clue(7236, tier = TrailTier.EASY, map = 17537, position = Position(2970, 3415, 0), casket = 7237), // easy_map006
        Clue(7238, tier = TrailTier.EASY, text = "Search the crates in Canifis.", position = Position(3509, 3497, 0), targetObjects = setOf(356)), // easy_vague031
    )
    val medium = listOf(
        Clue(3582, tier = TrailTier.MEDIUM, text = "11 degrees 03 minutes North|31 degrees 20 minutes East", position = Position(3443, 3515, 0), casket = 3583, coordinate = true), // medium_sextant014
        Clue(3584, tier = TrailTier.MEDIUM, text = "07 degrees 05 minutes North|30 degrees 56 minutes East", position = Position(3430, 3388, 0), casket = 3585, coordinate = true), // medium_sextant015
        Clue(3586, tier = TrailTier.MEDIUM, text = "11 degrees 41 minutes North|14 degrees 58 minutes East", position = Position(2919, 3535, 0), casket = 3587, coordinate = true), // medium_sextant016
        Clue(3588, tier = TrailTier.MEDIUM, text = "00 degrees 13 minutes South|13 degrees 58 minutes East", position = Position(2887, 3154, 0), casket = 3589, coordinate = true), // medium_sextant017
        Clue(3590, tier = TrailTier.MEDIUM, text = "00 degrees 18 minutes South|09 degrees 28 minutes East", position = Position(2743, 3151, 0), casket = 3591, coordinate = true), // medium_sextant018
        Clue(3592, tier = TrailTier.MEDIUM, text = "08 degrees 33 minutes North|01 degrees 39 minutes West", position = Position(2387, 3435, 0), casket = 3593, coordinate = true), // medium_sextant019
        Clue(3594, tier = TrailTier.MEDIUM, text = "11 degrees 05 minutes North|00 degrees 45 minutes West", position = Position(2416, 3516, 0), casket = 3595, coordinate = true), // medium_sextant020
        Clue(3598, tier = TrailTier.MEDIUM, map = 9196, position = Position(2658, 3488, 0), targetObjects = setOf(357)), // medium_map004
        Clue(3599, tier = TrailTier.MEDIUM, map = 9632, position = Position(2650, 3231, 0), casket = 3600), // medium_map005
        Clue(3601, tier = TrailTier.MEDIUM, map = 9720, position = Position(2565, 3248, 0), targetObjects = setOf(354)), // medium_map006
        Clue(3602, tier = TrailTier.MEDIUM, map = 9839, position = Position(2924, 3209, 0), casket = 3603), // medium_map007
        Clue(3604, tier = TrailTier.MEDIUM, text = "In a village made of bamboo,|look for some crates under one|of the houses.", position = Position(2800, 3074, 0), targetObjects = setOf(356)), // medium_riddle006
        Clue(3605, tier = TrailTier.MEDIUM, text = "Search the upstairs drawers of|a house in a village where|pirates are known to have a|good time.", position = Position(2809, 3165, 1), kind = Kind.DRAWER, key = 3606, keyNpcs = setOf(183)), // medium_riddle007
        Clue(3607, tier = TrailTier.MEDIUM, text = "Go to the village being|attacked by trolls, search the|drawers in one of the houses.", position = Position(2921, 3577, 0), kind = Kind.DRAWER, key = 3608, keyNpcs = setOf(1087)), // medium_riddle008
        Clue(3609, tier = TrailTier.MEDIUM, text = "A town with a different sort|of night-life is your|destination. Search for some|crates in one of the houses.", position = Position(3498, 3507, 0), targetObjects = setOf(356)), // medium_riddle009
        Clue(3610, tier = TrailTier.MEDIUM, text = "Find a crate close to the|monks that like to paaarty!", position = Position(2614, 3204, 0), targetObjects = setOf(354)), // medium_riddle010
        Clue(3611, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||ME IF", npcId = 676), // medium_anagram013
        Clue(3612, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||BAIL TRIMS", npcId = 171), // medium_anagram014
        Clue(3613, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||A BAS", npcId = 1070), // medium_anagram015
        Clue(3614, tier = TrailTier.MEDIUM, text = "Speak to Ulizius.", npcId = 1054), // medium_anagram016
        Clue(3615, tier = TrailTier.MEDIUM, text = "Speak to Roavar.", npcId = 1042), // medium_anagram017
        Clue(3616, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||AHA JAR", npcId = 962), // medium_anagram018
        Clue(3617, tier = TrailTier.MEDIUM, text = "Speak to Kangai Mau.", npcId = 846), // medium_anagram019
        Clue(3618, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||ICY FE", npcId = 1011), // medium_anagram020
        Clue(7274, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||DT RUN B", npcId = 1294, auxiliary = 7275, answer = 5), // medium_anagram021
        Clue(7276, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||GOT A BOY", npcId = 2519, auxiliary = 7277, answer = 11), // medium_anagram022
        Clue(7278, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||HICK JET", npcId = 725, auxiliary = 7279, answer = 38), // medium_anagram023
        Clue(7280, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||ARC O LINE", npcId = 696, auxiliary = 7281, answer = 11), // medium_anagram024
        Clue(7282, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||NOD MED", npcId = 3213, auxiliary = 7283, answer = 3), // medium_anagram025
        Clue(7284, tier = TrailTier.MEDIUM, text = "This anagram reveals|who to speak to next:||LARK IN DOG", npcId = 648, auxiliary = 7285, answer = 24), // medium_anagram026
        Clue(7286, tier = TrailTier.MEDIUM, map = 17687, position = Position(2536, 3865, 0), casket = 7287), // medium_map008
        Clue(7288, tier = TrailTier.MEDIUM, map = 17774, position = Position(3434, 3266, 0), casket = 7289), // medium_map009
        Clue(7290, tier = TrailTier.MEDIUM, map = 17888, position = Position(2454, 3230, 0), casket = 7291), // medium_map010
        Clue(7292, tier = TrailTier.MEDIUM, map = 17907, position = Position(2578, 3597, 0), casket = 7293), // medium_map011
        Clue(7294, tier = TrailTier.MEDIUM, map = 18055, position = Position(2667, 3562, 0), casket = 7295), // medium_map012
        Clue(7296, tier = TrailTier.MEDIUM, text = "The dead, red dragon watches|over this chest. He must|really dig the view.", position = Position(3353, 3332, 0), kind = Kind.CHEST_CLOSED, key = 7297, keyNpcs = setOf(12, 3246, 3247, 3248, 3249, 3250, 3251, 3252, 3253, 3255, 3256, 3257, 3258, 3259, 3260, 3261, 3262, 3263)), // medium_riddle011
        Clue(7298, tier = TrailTier.MEDIUM, text = "Go to this building to be|illuminated, and check the|drawers while you are there.", position = Position(2512, 3641, 1), kind = Kind.DRAWER, key = 7299, keyNpcs = setOf(1317)), // medium_riddle012
        Clue(7300, tier = TrailTier.MEDIUM, text = "Try not to step on any aquatic|nasties while searching this|crate.", position = Position(2765, 3274, 0), targetObjects = setOf(354)), // medium_riddle013
        Clue(7301, tier = TrailTier.MEDIUM, text = "Probably filled with wizards|socks.", position = Position(3116, 9562, 0), kind = Kind.DRAWER, key = 7302, keyNpcs = setOf(13)), // medium_riddle014
        Clue(7303, tier = TrailTier.MEDIUM, text = "This crate is mine, all mine,|even if it is in the middle of|the desert.", position = Position(3289, 3022, 0), targetObjects = setOf(356)), // medium_riddle015
        Clue(7304, tier = TrailTier.MEDIUM, text = "This crate holds a better|reward than a broken arrow.", position = Position(2671, 3437, 0), targetObjects = setOf(356)), // medium_riddle016
        Clue(7305, tier = TrailTier.MEDIUM, text = "22 degrees 30 minutes North|03 degrees 01 minutes East", position = Position(2537, 3881, 0), casket = 7306, coordinate = true), // medium_sextant021
        Clue(7307, tier = TrailTier.MEDIUM, text = "05 degrees 20 minutes South|04 degrees 28 minutes East", position = Position(2583, 2990, 0), casket = 7308, coordinate = true), // medium_sextant022
        Clue(7309, tier = TrailTier.MEDIUM, text = "01 degrees 18 minutes South|14 degrees 15 minutes East", position = Position(2896, 3119, 0), casket = 7310, coordinate = true), // medium_sextant023
        Clue(7311, tier = TrailTier.MEDIUM, text = "09 degrees 48 minutes North|17 degrees 39 minutes East", position = Position(3005, 3475, 0), casket = 7312, coordinate = true), // medium_sextant024
        Clue(7313, tier = TrailTier.MEDIUM, text = "00 degrees 20 minutes South|23 degrees 15 minutes East", position = Position(3184, 3150, 0), casket = 7314, coordinate = true), // medium_sextant025
        Clue(7315, tier = TrailTier.MEDIUM, text = "14 degrees 54 minutes North|09 degrees 13 minutes East", position = Position(2735, 3638, 0), casket = 7316, coordinate = true), // medium_sextant026
        Clue(7317, tier = TrailTier.MEDIUM, text = "03 degrees 35 minutes South|13 degrees 35 minutes East", position = Position(2875, 3046, 0), casket = 7318, coordinate = true), // medium_sextant027
    )
    val hard = listOf(
        Clue(3520, tier = TrailTier.HARD, map = 9043, position = Position(2616, 3077, 0), casket = 3521), // hard_map002
        Clue(3522, tier = TrailTier.HARD, map = 9359, position = Position(2488, 3308, 0), casket = 3523), // hard_map003
        Clue(3524, tier = TrailTier.HARD, map = 9454, position = Position(2459, 3179, 0), targetObjects = setOf(357)), // hard_map004
        Clue(3525, tier = TrailTier.HARD, map = 9507, position = Position(3026, 3628, 0), targetObjects = setOf(354)), // hard_map005
        Clue(3526, tier = TrailTier.HARD, text = "16 degrees 03 minutes North|14 degrees 07 minutes East", position = Position(2892, 3675, 0), casket = 3527, coordinate = true), // hard_sextant014
        Clue(3528, tier = TrailTier.HARD, text = "16 degrees 31 minutes North|12 degrees 54 minutes East", position = Position(2853, 3690, 0), casket = 3529, coordinate = true), // hard_sextant015
        Clue(3530, tier = TrailTier.HARD, text = "05 degrees 50 minutes South|10 degrees 05 minutes East", position = Position(2763, 2974, 0), casket = 3531, coordinate = true), // hard_sextant016
        Clue(3532, tier = TrailTier.HARD, text = "08 degrees 26 minutes South|10 degrees 28 minutes East", position = Position(2775, 2891, 0), casket = 3533, coordinate = true), // hard_sextant017
        Clue(3534, tier = TrailTier.HARD, text = "07 degrees 43 minutes South|12 degrees 26 minutes East", position = Position(2838, 2914, 0), casket = 3535, coordinate = true), // hard_sextant018
        Clue(3536, tier = TrailTier.HARD, text = "08 degrees 05 minutes South|15 degrees 56 minutes East", position = Position(2950, 2902, 0), casket = 3537, coordinate = true), // hard_sextant019
        Clue(3538, tier = TrailTier.HARD, text = "04 degrees 16 minutes South|16 degrees 16 minutes East", position = Position(2961, 3024, 0), casket = 3539, coordinate = true), // hard_sextant020
        Clue(3540, tier = TrailTier.HARD, text = "06 degrees 11 minutes South|15 degrees 07 minutes East", position = Position(2924, 2963, 0), casket = 3541, coordinate = true), // hard_sextant021
        Clue(3542, tier = TrailTier.HARD, text = "05 degrees 37 minutes North|31 degrees 15 minutes East", position = Position(3440, 3341, 0), casket = 3543, coordinate = true), // hard_sextant022
        Clue(3544, tier = TrailTier.HARD, text = "08 degrees 03 minutes North|31 degrees 16 minutes East", position = Position(3441, 3419, 0), casket = 3545, coordinate = true), // hard_sextant023
        Clue(3546, tier = TrailTier.HARD, text = "04 degrees 03 minutes South|03 degrees 11 minutes East", position = Position(2542, 3031, 0), casket = 3547, coordinate = true), // hard_sextant024
        Clue(3548, tier = TrailTier.HARD, text = "04 degrees 05 minutes South|04 degrees 24 minutes East", position = Position(2581, 3030, 0), casket = 3549, coordinate = true), // hard_sextant025
        Clue(3552, tier = TrailTier.HARD, text = "03 degrees 45 minutes South|22 degrees 45 minutes East", position = Position(3168, 3041, 0), casket = 3553, coordinate = true), // hard_sextant027
        Clue(3556, tier = TrailTier.HARD, text = "18 degrees 50 minutes North|20 degrees 26 minutes East", position = Position(3094, 3764, 0), casket = 3557, coordinate = true, guardian = true), // hard_sextant029
        Clue(3558, tier = TrailTier.HARD, text = "24 degrees 24 minutes North|26 degrees 24 minutes East", position = Position(3285, 3942, 0), casket = 3559, coordinate = true, guardian = true), // hard_sextant030
        Clue(3560, tier = TrailTier.HARD, text = "00 degrees 00 minutes North|07 degrees 13 minutes West", position = Position(2209, 3161, 0), casket = 3561, coordinate = true), // hard_sextant031
        Clue(3562, tier = TrailTier.HARD, text = "01 degrees 24 minutes North|08 degrees 05 minutes West", position = Position(2181, 3206, 0), casket = 3563, coordinate = true), // hard_sextant032
        Clue(3564, tier = TrailTier.HARD, text = "There is no 'worthier' lord.", npcId = 1182, auxiliary = 3565, puzzle = true), // hard_riddle018
        Clue(3566, tier = TrailTier.HARD, text = "Often sought out by scholars|of histories past, find me|where words of wisdom speak|volumes.", npcId = 618, auxiliary = 3567, puzzle = true), // hard_riddle019
        Clue(3568, tier = TrailTier.HARD, text = "Identify the back of this|over-acting brother. (He's a|long way from home.)", npcId = 1008, auxiliary = 3569, puzzle = true), // hard_riddle020
        Clue(3570, tier = TrailTier.HARD, text = "This aviator is at the peak of|his profession.", npcId = 170, auxiliary = 3571, puzzle = true), // hard_riddle021
        Clue(3572, tier = TrailTier.HARD, text = "Read 'How to breed scorpions.'|By O.W.Thathurt.", position = Position(2702, 3409, 1), targetObjects = setOf(380)), // hard_riddle022
        Clue(3573, tier = TrailTier.HARD, text = "A great view - watch the|rapidly drying hides get|splashed. Check the box you|are sitting on.", position = Position(2523, 3493, 1), targetObjects = setOf(359)), // hard_riddle023
        Clue(3574, tier = TrailTier.HARD, text = "Must be full of railings.", position = Position(2576, 3464, 0), targetObjects = setOf(354)), // hard_riddle024
        Clue(3575, tier = TrailTier.HARD, text = "Citric cellar.", npcId = 603, auxiliary = 3576, puzzle = true), // hard_riddle025
        Clue(3577, tier = TrailTier.HARD, text = "'Small shoe.' Often found with|rod on mushroom.", npcId = 162, auxiliary = 3578, puzzle = true), // hard_riddle026
        Clue(3579, tier = TrailTier.HARD, text = "When no weapons are at hand,|then is the time to reflect.|In Saradomin's name,|redemption draws closer...", position = Position(2818, 3351, 0), kind = Kind.EMPTY_DRAWER), // hard_riddle027
        Clue(3580, tier = TrailTier.HARD, text = "Mine was the strangest birth|under the sun. I left the|crimson sack, yet life had not|begun. Entered the world, and|yet was seen by none.", position = Position(2832, 9586, 0), casket = 3581), // hard_riddle028
        Clue(7239, tier = TrailTier.HARD, map = 17620, position = Position(3021, 3912, 0), casket = 7240), // hard_map006
        Clue(7241, tier = TrailTier.HARD, map = 17634, position = Position(2722, 3338, 0), casket = 7242), // hard_map007
        Clue(7243, tier = TrailTier.HARD, text = "And so on, and so on, and so|on. Walking from the land of|many unimportant things leads|to a choice of paths.", position = Position(2591, 3879, 0), casket = 7244), // hard_riddle029
        Clue(7245, tier = TrailTier.HARD, text = "Covered in shadows, the centre|of the circle is where you|will find the answer.", position = Position(3488, 3289, 0), casket = 7246), // hard_riddle030
        Clue(7247, tier = TrailTier.HARD, text = "This village has a problem|with cartloads of the undead.|Try checking the bookcase to|find an answer.", position = Position(2833, 2991, 0), targetObjects = setOf(394)), // hard_riddle031
        Clue(7248, tier = TrailTier.HARD, text = "The cheapest water for miles|around, but they react badly|to religious icons.", position = Position(3178, 2987, 0), targetObjects = setOf(354)), // hard_riddle032
        Clue(7249, tier = TrailTier.HARD, text = "Probably filled with books on|magic.", position = Position(3096, 9571, 0), targetObjects = setOf(380)), // hard_riddle033
        Clue(7250, tier = TrailTier.HARD, text = "You have all of the elements|available to solve this clue.|Fortunately you do not have to|go as far as to stand in a|draft.", position = Position(2723, 9891, 0), targetObjects = setOf(357)), // hard_riddle034
        Clue(7251, tier = TrailTier.HARD, text = "It seems to have reached the|end of the line, and it's|still empty.", position = Position(3041, 9820, 0), targetObjects = setOf(6045)), // hard_riddle035
        Clue(7252, tier = TrailTier.HARD, text = "When you get tired of|fighting, go deep, deep down|until you need an antidote.", position = Position(2576, 9583, 0), targetObjects = setOf(357)), // hard_riddle036
        Clue(7253, tier = TrailTier.HARD, text = "His head might be hollow, but|the crates nearby are filled|with surprises.", position = Position(3478, 3091, 0), targetObjects = setOf(354)), // hard_riddle037
        Clue(7254, tier = TrailTier.HARD, text = "If you look closely enough, it|seems that the archers have|lost more than their needles.", position = Position(2671, 3415, 0), targetObjects = setOf(300)), // hard_riddle038
        Clue(7256, tier = TrailTier.HARD, text = "04 degrees 41 minutes North|03 degrees 09 minutes West", position = Position(2339, 3311, 0), casket = 7257, coordinate = true), // hard_sextant033
        Clue(7258, tier = TrailTier.HARD, text = "06 degrees 00 minutes South|21 degrees 48 minutes East", position = Position(3138, 2969, 0), casket = 7259, coordinate = true), // hard_sextant034
        Clue(7260, tier = TrailTier.HARD, text = "18 degrees 22 minutes North|16 degrees 33 minutes East", position = Position(2970, 3749, 0), casket = 7261, coordinate = true, guardian = true), // hard_sextant035
        Clue(7262, tier = TrailTier.HARD, text = "13 degrees 46 minutes North|21 degrees 01 minutes East", position = Position(3113, 3602, 0), casket = 7263, coordinate = true, guardian = true), // hard_sextant036
        Clue(7264, tier = TrailTier.HARD, text = "16 degrees 35 minutes North|27 degrees 01 minutes East", position = Position(3305, 3692, 0), casket = 7265, coordinate = true, guardian = true), // hard_sextant037
        Clue(7266, tier = TrailTier.HARD, text = "17 degrees 50 minutes North|08 degrees 30 minutes East", position = Position(2712, 3732, 0), casket = 7267, coordinate = true), // hard_sextant038
        Clue(7268, tier = TrailTier.HARD, text = "This anagram reveals|who to speak to next:||C ON GAME HOC", npcId = 2802, auxiliary = 7269, answer = 6), // hard_anagram001
        Clue(7270, tier = TrailTier.HARD, text = "This anagram reveals|who to speak to next:||BY LOOK", npcId = 471, auxiliary = 7271, answer = 13), // hard_anagram002
        Clue(7272, tier = TrailTier.HARD, text = "This anagram reveals|who to speak to next:||O BIRDZ A ZANY EN PC", npcId = 437, auxiliary = 7273, answer = 33), // hard_anagram003
    )
    val all = easy + medium + hard

    /**
     * The sliding pieces each new puzzle box shows, as the id before its first piece.
     */
    val puzzlePieces = mapOf(3565 to 3618, 3567 to 3618, 3569 to 3642, 3571 to 3642, 3576 to 3642, 3578 to 3618)
    val challenges = mapOf(
        7269 to "How many gnomes on the Gnome|ball field have red patches on|their uniforms?",
        7271 to "How many flowers are there in|the clearing below this|platform?",
        7273 to "How many Banana Trees are|there in the plantation?",
        7275 to "How many people are waiting|for the next bard to perform?",
        7277 to "How many buildings are in the|village?",
        7279 to "How many graves are there in|the city graveyard?",
        7281 to "How many fishermen are there|on the fishing platform?",
        7283 to "How many pigeon cages are|there around the back of|Jerico's house?",
        7285 to "How many bookcases are there|in the palace library?",
    )
    val trailItems = setOf(
        3490, 3491, 3492, 3493, 3494, 3495, 3496, 3497, 3498, 3499, 3500, 3501, 3502, 3503, 3504,
        3506, 3507, 3508, 3509, 3510, 3511, 3512, 3513, 3514, 3515, 3516, 3517, 3518, 3519, 3520,
        3521, 3522, 3523, 3524, 3525, 3526, 3527, 3528, 3529, 3530, 3531, 3532, 3533, 3534, 3535,
        3536, 3537, 3538, 3539, 3540, 3541, 3542, 3543, 3544, 3545, 3546, 3547, 3548, 3549, 3552,
        3553, 3556, 3557, 3558, 3559, 3560, 3561, 3562, 3563, 3564, 3566, 3568, 3570, 3572, 3573,
        3574, 3575, 3577, 3579, 3580, 3581, 3582, 3583, 3584, 3585, 3586, 3587, 3588, 3589, 3590,
        3591, 3592, 3593, 3594, 3595, 3598, 3599, 3600, 3601, 3602, 3603, 3604, 3605, 3607, 3609,
        3610, 3611, 3612, 3613, 3614, 3615, 3616, 3617, 3618, 7236, 7237, 7238, 7239, 7240, 7241,
        7242, 7243, 7244, 7245, 7246, 7247, 7248, 7249, 7250, 7251, 7252, 7253, 7254, 7256, 7257,
        7258, 7259, 7260, 7261, 7262, 7263, 7264, 7265, 7266, 7267, 7268, 7270, 7272, 7274, 7276,
        7278, 7280, 7282, 7284, 7286, 7287, 7288, 7289, 7290, 7291, 7292, 7293, 7294, 7295, 7296,
        7298, 7300, 7301, 7303, 7304, 7305, 7306, 7307, 7308, 7309, 7310, 7311, 7312, 7313, 7314,
        7315, 7316, 7317, 7318,
    )
}
