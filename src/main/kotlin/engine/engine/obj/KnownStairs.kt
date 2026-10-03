package engine.obj

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.`object`.GameObject

/**
 * Where stairs lead in the original game, keyed by the stairs' position. Stairs that aren't listed use
 * [StairDestination]'s pairing rule instead.
 *
 * Quest and item requirements aren't checked because Luna doesn't have that content yet, so players are treated as
 * meeting them.
 *
 * @author TheLining
 */
object KnownStairs {

    /**
     * Where climbing takes a player standing on a tile.
     */
    fun interface Landing {

        /**
         * Returns where a player standing on [player] lands.
         */
        fun from(player: Position): Position
    }

    /**
     * Where stairs lead: [up] when climbing up and [down] when climbing down, or the same landing for both when their
     * "Climb" option doesn't ask. [message] is sent after climbing. [choices] replace the usual up and down options
     * the "Climb" option asks with.
     */
    class Stairs(val up: Landing?, val down: Landing?, val message: String? = null,
                 val choices: Pair<String, String>? = null)

    /**
     * Returns where [stairs] lead, or `null` if that isn't known.
     */
    fun forStairs(stairs: GameObject): Stairs? = ALL[stairs.position] ?: rotated(stairs)

    /**
     * Stairs whose landing depends only on which way they face: tile offsets from the stairs for each rotation
     * (west, north, east, south), climbing up for the first and down for the second.
     */
    private val ROTATED = mapOf(
        // Tree Gnome Stronghold stairs.
        1742 to listOf(Triple(2, 0, 1), Triple(0, -1, 1), Triple(-1, 1, 1), Triple(1, 2, 1)),
        1744 to listOf(Triple(0, -1, -1), Triple(-1, 0, -1), Triple(0, 1, -1), Triple(1, 0, -1)),

        // Troll Stronghold.
        3788 to listOf(Triple(0, 3, 1), Triple(3, 0, 1), Triple(0, -1, 1), null),
        3789 to listOf(Triple(0, -2, -1), Triple(-2, 0, -1), Triple(0, 3, -1), null)
    )

    /**
     * Returns where [stairs] lead if that depends only on which way they face, or `null` otherwise.
     */
    private fun rotated(stairs: GameObject): Stairs? {
        val (dx, dy, dz) = ROTATED[stairs.id]?.get(stairs.direction.id) ?: return null
        val landing = tile(stairs.position.x + dx, stairs.position.y + dy, stairs.position.z + dz)
        return if (dz > 0) Stairs(up = landing, down = null) else Stairs(up = null, down = landing)
    }

    /**
     * Always lands on the same tile.
     */
    private fun tile(x: Int, y: Int, z: Int) = Landing { Position(x, y, z) }

    /**
     * Lands on the player's own tile, moved.
     */
    private fun offset(dx: Int, dy: Int, dz: Int) = Landing { it.translate(dx, dy, dz) }

    /**
     * Lands on a random tile near [x], [y] and [z].
     */
    private fun area(x: Int, y: Int, z: Int, dx: IntRange, dy: IntRange) =
        Landing { Position(x + rand(dx), y + rand(dy), z) }

    private fun up(x: Int, y: Int, z: Int, landing: Landing, message: String? = null) =
        Position(x, y, z) to Stairs(up = landing, down = null, message = message)

    private fun down(x: Int, y: Int, z: Int, landing: Landing, message: String? = null) =
        Position(x, y, z) to Stairs(up = null, down = landing, message = message)

    private fun climb(x: Int, y: Int, z: Int, up: Landing, down: Landing, choices: Pair<String, String>? = null) =
        Position(x, y, z) to Stairs(up, down, choices = choices)

    private fun either(x: Int, y: Int, z: Int, landing: Landing) = Position(x, y, z) to Stairs(landing, landing)

    /**
     * The known stairs by position.
     */
    private val ALL = mapOf(
        // Al Kharid General Store.
        up(3312, 3185, 0, tile(3314, 3185, 1)),
        down(3313, 3185, 1, tile(3313, 3184, 0)),

        // Ape Atoll.
        up(2795, 2794, 0, offset(0, 4, 1), "You climb up the stairs."),
        up(2799, 2794, 0, offset(0, 4, 1), "You climb up the stairs."),
        down(2795, 2795, 1, offset(0, -4, -1), "You climb down the stairs."),
        down(2799, 2795, 1, offset(0, -4, -1), "You climb down the stairs."),

        // Ardougne Castle.
        up(2571, 3295, 0, tile(2571, 3294, 1)),
        down(2571, 3295, 1, tile(2571, 3298, 0)),

        // Ardougne houses.
        up(2646, 3302, 0, tile(2648, 3301, 1)),
        up(2648, 3294, 0, tile(2649, 3296, 1)),
        up(2648, 3310, 0, tile(2649, 3312, 1)),
        up(2662, 3321, 0, tile(2665, 3321, 1)),
        up(2673, 3300, 0, tile(2675, 3300, 1)),
        up(2662, 3291, 0, tile(2661, 3291, 1)),
        down(2647, 3302, 1, tile(2645, 3302, 0)),
        down(2648, 3295, 1, tile(2648, 3293, 0)),
        down(2649, 3311, 1, tile(2650, 3311, 0)),
        down(2662, 3291, 1, tile(2662, 3294, 0)),
        down(2663, 3321, 1, tile(2661, 3321, 0)),
        down(2674, 3300, 1, tile(2674, 3299, 0)),

        // Arlena's house, Ardougne.
        up(2575, 3331, 0, offset(4, 0, 1)),
        down(2576, 3331, 1, offset(-4, 0, -1)),

        // Black Arm Gang hideout.
        up(3188, 3389, 0, tile(3188, 3392, 1)),
        down(3188, 3390, 1, tile(3188, 3388, 0)),

        // Black Knight's Fortress.
        up(3010, 3515, 0, tile(3012, 3515, 1)),
        down(3011, 3515, 1, tile(3011, 3514, 0)),
        up(3010, 3516, 2, tile(3010, 3515, 3)),
        up(3029, 3506, 2, tile(3028, 3507, 3)),
        down(3010, 3516, 3, tile(3010, 3515, 2)),
        down(3029, 3507, 3, tile(3028, 3507, 2)),

        // Blue Moon Inn, Varrock.
        up(3227, 3393, 0, tile(3230, 3393, 1)),
        down(3228, 3393, 1, tile(3226, 3393, 0)),

        // Brimhaven Dungeon.
        up(2635, 9514, 0, tile(2636, 9510, 2)),
        up(2648, 9592, 0, tile(2643, 9595, 2)),
        down(2635, 9511, 2, tile(2636, 9517, 0)),
        down(2644, 9593, 2, tile(2649, 9591, 0)),

        // Brimhaven houses.
        up(2759, 3196, 0, tile(2759, 3195, 1)),
        up(2796, 3195, 0, tile(2796, 3198, 1)),
        down(2759, 3196, 1, tile(2759, 3199, 0)),
        down(2796, 3196, 1, tile(2796, 3194, 0)),

        // Burthorpe castle.
        up(2897, 3566, 0, tile(2897, 3569, 1)),
        down(2897, 3567, 1, tile(2897, 3565, 0)),

        // Burthorpe games room.
        up(2207, 4935, 0, tile(2207, 4934, 1)),
        up(2212, 4941, 0, tile(2212, 4944, 1)),
        down(2899, 3566, 0, area(2206, 4933, 1, dx = -1..1, dy = 0..1)),
        up(2205, 4935, 1, area(2899, 3564, 0, dx = -1..1, dy = 0..1)),
        down(2207, 4935, 1, tile(2207, 4938, 0)),
        down(2212, 4942, 1, tile(2212, 4940, 0)),

        // Burthorpe pub.
        up(2914, 3539, 0, tile(2914, 3542, 1)),
        down(2914, 3540, 1, tile(2914, 3538, 0)),

        // Camelot castle.
        up(2750, 3510, 0, tile(2750, 3513, 1)),
        down(2750, 3511, 1, tile(2750, 3509, 0)),

        // Carnillean house, Ardougne.
        up(2568, 3268, 0, offset(0, -4, 1)),
        down(2568, 3268, 1, offset(0, 4, -1)),

        // Castle Wars.
        either(2380, 3127, 0, tile(2379, 3127, 1)),
        either(2382, 3131, 0, Landing { if (it.x > 2382) Position(2382, 3130) else Position(2383, 3133) }),
        either(2417, 3074, 0, Landing { if (it.x < 2417) Position(2417, 3077) else Position(2416, 3074) }),
        either(2419, 3078, 0, tile(2420, 3080, 1)),
        either(2369, 3126, 1, tile(2369, 3127, 2)),
        either(2380, 3127, 1, tile(2380, 3130, 0)),
        either(2419, 3080, 1, tile(2419, 3077, 0)),
        either(2428, 3081, 1, tile(2430, 3080, 2)),
        either(2369, 3126, 2, tile(2372, 3126, 1)),
        either(2374, 3131, 2, tile(2373, 3133, 3)),
        either(2425, 3074, 2, tile(2426, 3074, 3)),
        either(2430, 3081, 2, tile(2427, 3081, 1)),
        either(2374, 3133, 3, tile(2374, 3130, 2)),
        either(2425, 3074, 3, tile(2425, 3077, 2)),

        // Champions' Guild.
        up(3188, 3355, 0, tile(3188, 3354, 1)),
        down(3188, 3355, 1, tile(3188, 3358, 0)),

        // Clock Tower.
        up(2572, 3240, 0, tile(2571, 3241, 1)),
        climb(2572, 3240, 1, up = tile(2571, 3241, 2), down = tile(2572, 3242, 0)),
        down(2572, 3241, 2, tile(2572, 3242, 1)),

        // Cooking Guild.
        up(3144, 3447, 0, tile(3143, 3448, 1)),
        down(3144, 3448, 1, tile(3143, 3448, 0)),

        // Crafting Guild.
        up(2931, 3282, 0, tile(2933, 3282, 1)),
        down(2932, 3282, 1, tile(2932, 3281, 0)),

        // Dark Wizards' Tower.
        up(2907, 3334, 0, tile(2908, 3336, 1)),
        climb(2907, 3334, 1, up = tile(2908, 3336, 2), down = tile(2908, 3336, 0)),
        down(2908, 3335, 2, tile(2908, 3336, 1)),

        // Draynor Manor.
        up(3108, 3362, 0, offset(0, 5, 1)),
        up(3104, 3362, 1, tile(3105, 3364, 2)),
        down(3108, 3364, 1, offset(0, -5, -1)),
        down(3105, 3363, 2, tile(3106, 3363, 1)),

        // Draynor Manor crypt.
        up(3077, 9768, 0, tile(3115, 3356, 0)),
        down(3115, 3357, 0, tile(3077, 9771, 0), "You walk down the stairs..."),

        // Elemental Workshop.
        down(2710, 3497, 0, tile(2716, 9888, 0)),
        up(2714, 9887, 0, tile(2709, 3497, 0)),

        // Elena's house, Ardougne.
        up(2591, 3334, 0, tile(2594, 3334, 1)),
        down(2592, 3334, 1, tile(2590, 3334, 0)),

        // Etceteria castle.
        up(2613, 3867, 0, tile(2615, 3867, 1)),
        down(2614, 3867, 1, tile(2614, 3866, 0)),

        // Falador castle courtyard. The bottom piece is placed two tiles further west than in 2004 but covers the same
        // tiles, and the top piece hasn't moved.
        up(2968, 3347, 0, tile(2968, 3348, 1)),
        down(2968, 3347, 1, tile(2971, 3347, 0)),

        // Falador dwarf house.
        up(3011, 3338, 0, tile(3011, 3337, 1)),
        up(3018, 3343, 0, tile(3021, 3344, 1)),
        up(3021, 3332, 0, tile(3024, 3333, 1)),
        down(3011, 3338, 1, tile(3010, 3338, 0)),
        down(3019, 3343, 1, tile(3017, 3344, 0)),
        down(3022, 3332, 1, tile(3020, 3333, 0)),

        // Falador houses.
        up(3034, 3363, 0, tile(3036, 3363, 1)),
        up(3044, 3381, 0, tile(3044, 3384, 1)),
        up(3045, 3363, 0, tile(3045, 3366, 1)),
        up(3048, 3352, 0, tile(3049, 3354, 1)),
        down(3035, 3363, 1, tile(3036, 3363, 0)),
        down(3044, 3382, 1, tile(3044, 3380, 0)),
        down(3045, 3364, 1, tile(3045, 3362, 0)),
        down(3049, 3353, 1, tile(3049, 3354, 0)),

        // Falador mine.
        down(3058, 3376, 0, tile(3058, 9776, 0)),
        up(3059, 9776, 0, tile(3061, 3376, 0)),

        // Falador shield shop.
        up(2973, 3384, 0, tile(2972, 3385, 1)),
        down(2973, 3385, 1, tile(2972, 3384, 0)),

        // Falador smithy.
        up(2971, 3370, 0, offset(0, 4, 1)),
        down(2971, 3371, 1, offset(0, -4, -1)),

        // Fight Arena.
        up(2613, 3139, 0, tile(2612, 3139, 1)),
        down(2613, 3139, 1, tile(2616, 3139, 0)),

        // Flying Horse Inn, Ardougne.
        up(2566, 3322, 0, tile(2566, 3321, 1)),
        up(2572, 3325, 0, tile(2574, 3325, 1)),
        down(2566, 3322, 1, tile(2565, 3322, 0)),
        down(2573, 3325, 1, tile(2573, 3324, 0)),

        // Grail castle.
        up(2633, 4680, 0, tile(2634, 4682, 1)),
        up(2648, 4683, 0, tile(2649, 4685, 1)),
        up(2761, 4680, 0, tile(2762, 4682, 1)),
        up(2776, 4683, 0, tile(2777, 4685, 1)),
        down(2634, 4681, 1, tile(2635, 4681, 0)),
        up(2640, 4682, 1, tile(2640, 4685, 2)),
        down(2649, 4684, 1, tile(2650, 4684, 0)),
        down(2762, 4681, 1, tile(2763, 4681, 0)),
        up(2768, 4682, 1, tile(2768, 4685, 2)),
        down(2777, 4684, 1, tile(2778, 4684, 0)),
        down(2640, 4683, 2, tile(2640, 4681, 1)),
        down(2768, 4683, 2, tile(2768, 4681, 1)),

        // Hadley's house.
        up(2517, 3429, 0, tile(2518, 3431, 1)),
        down(2518, 3430, 1, tile(2519, 3430, 0)),

        // Hair of the Dog tavern, Canifis.
        up(3501, 3475, 0, tile(3500, 3476, 1)),
        down(3501, 3476, 1, tile(3501, 3477, 0)),

        // Haunted Mine.
        down(2692, 4436, 0, tile(2758, 4454, 0)),
        up(2709, 4591, 0, tile(2691, 4437, 0)),
        up(2731, 4561, 0, tile(2750, 4437, 0)),
        down(2746, 4436, 0, tile(2811, 4454, 0)),
        up(2755, 4452, 0, tile(2691, 4437, 0)),
        up(2790, 4589, 0, tile(3454, 3242, 0)),
        up(2812, 4452, 0, tile(2750, 4437, 0)),
        down(3452, 3243, 0, tile(2792, 4592, 0)),

        // Hazeel Cult sewers.
        up(2570, 9683, 0, tile(2587, 3237, 0), "You climb up the stairs."),

        // Heroes' Guild.
        up(2895, 3513, 0, tile(2897, 3513, 1)),
        down(2896, 3513, 1, tile(2896, 3512, 0)),

        // Julia's house, Varrock.
        up(3156, 3435, 0, tile(3155, 3435, 1)),
        down(3156, 3435, 1, tile(3159, 3435, 0)),

        // Keep Le Faye.
        up(2769, 3404, 0, tile(2769, 3407, 1)),
        up(2769, 3398, 1, tile(2769, 3401, 2)),
        down(2769, 3405, 1, tile(2769, 3403, 0)),
        down(2769, 3399, 2, tile(2769, 3397, 1)),

        // Legends' Guild.
        down(2724, 3374, 0, tile(2727, 9774, 0)),
        up(2724, 9774, 0, tile(2723, 3374, 0)),
        up(2732, 3377, 0, tile(2732, 3380, 1)),
        down(2732, 3378, 1, tile(2732, 3376, 0)),

        // Lighthouse, and its copy for Horror from the Deep.
        up(2506, 3640, 0, tile(2505, 3641, 1)),
        up(2442, 4600, 0, tile(2441, 4601, 1)),
        climb(2506, 3640, 1, up = tile(2505, 3641, 2), down = tile(2506, 3642, 0),
              choices = "Climb Up." to "Climb Down."),
        climb(2442, 4600, 1, up = tile(2441, 4601, 2), down = tile(2442, 4602, 0),
              choices = "Climb Up." to "Climb Down."),
        down(2506, 3641, 2, tile(2506, 3642, 1)),
        down(2442, 4601, 2, tile(2442, 4602, 1)),

        // Lord Handelmort's mansion.
        up(2631, 3322, 0, tile(2631, 3321, 1), "You climb up the stairs."),
        down(2631, 3322, 1, tile(2631, 3325, 0)),

        // Lumbridge Castle.
        up(3204, 3207, 0, tile(3205, 3209, 1)),
        up(3204, 3229, 0, tile(3205, 3228, 1)),
        climb(3204, 3207, 1, up = tile(3205, 3209, 2), down = tile(3205, 3209, 0)),
        climb(3204, 3229, 1, up = tile(3206, 3229, 2), down = tile(3205, 3228, 0)),
        down(3205, 3208, 2, tile(3205, 3209, 1)),
        down(3205, 3229, 2, tile(3205, 3228, 1)),

        // Miscellania castle.
        climb(2505, 3848, 1, up = offset(0, 0, 1), down = offset(0, 0, -1)),
        climb(2505, 3871, 1, up = offset(0, 0, 1), down = offset(0, 0, -1)),
        climb(2505, 3848, 2, up = offset(0, 0, 1), down = offset(0, 0, -1)),
        climb(2505, 3871, 2, up = offset(0, 0, 1), down = offset(0, 0, -1)),

        // Monastery.
        up(2595, 3208, 0, tile(2597, 3208, 1)),
        up(2616, 3208, 0, tile(2618, 3208, 1)),
        down(2596, 3208, 1, tile(2596, 3207, 0)),
        down(2617, 3208, 1, tile(2617, 3207, 0)),

        // Morgan's house, Draynor.
        up(3099, 3266, 0, tile(3102, 3266, 1)),
        down(3100, 3266, 1, tile(3098, 3266, 0)),

        // Necromancer's tower.
        up(2669, 3243, 0, tile(2669, 3246, 1)),
        down(2669, 3244, 1, tile(2669, 3242, 0)),

        // Party Room.
        up(2728, 3460, 0, tile(2729, 3462, 1)),
        up(2746, 3460, 0, tile(2745, 3461, 1)),
        down(2729, 3461, 1, tile(2730, 3461, 0)),
        down(2746, 3461, 1, tile(2746, 3462, 0)),

        // Paterdomus Temple.
        up(3417, 3484, 0, tile(3416, 3485, 1)),
        up(3417, 3492, 0, tile(3416, 3493, 1)),
        down(3417, 3485, 1, tile(3417, 3486, 0)),
        down(3417, 3493, 1, tile(3417, 3494, 0)),

        // Plague house, West Ardougne.
        down(2536, 3268, 0, tile(2537, 9670, 0), "You walk down the stairs..."),
        up(2536, 9671, 0, tile(2536, 3271, 0), "You walk up the stairs..."),

        // Port Sarim.
        up(3023, 3261, 0, tile(3025, 3261, 1)),
        up(3026, 3248, 0, tile(3026, 3247, 1)),
        down(3024, 3261, 1, tile(3025, 3261, 0)),
        down(3026, 3248, 1, tile(3025, 3248, 0)),

        // Rehnison house, West Ardougne.
        up(2527, 3332, 0, tile(2527, 3331, 1), "You walk up the stairs."),
        down(2527, 3332, 1, tile(2527, 3331, 0), "You walk down the stairs."),

        // Rimmington house.
        up(2965, 3215, 0, tile(2968, 3215, 1)),
        down(2966, 3215, 1, tile(2964, 3215, 0)),

        // Rising Sun Inn, Falador.
        up(2959, 3369, 0, offset(0, 4, 1)),
        down(2959, 3370, 1, offset(0, -4, -1)),

        // Rogues' Castle.
        up(3280, 3936, 0, tile(3282, 3936, 1)),
        climb(3280, 3936, 1, up = tile(3282, 3936, 2), down = tile(3282, 3936, 0)),
        climb(3280, 3936, 2, up = tile(3282, 3936, 3), down = tile(3282, 3936, 1)),
        down(3281, 3936, 3, tile(3282, 3936, 2)),

        // Ruins of Uzer.
        up(2721, 4884, 0, tile(3491, 3090, 0)),
        down(3492, 3090, 0, tile(2721, 4886, 0)),

        // Sanfew's house, Taverley.
        up(2898, 3428, 0, tile(2898, 3427, 1)),
        down(2898, 3428, 1, tile(2897, 3428, 0)),

        // Seers' Village pub.
        up(2698, 3496, 0, tile(2698, 3495, 1)),
        down(2698, 3496, 1, tile(2697, 3496, 0)),

        // Temple of Ikov.
        up(2638, 9740, 0, tile(2654, 9809, 0)),
        up(2638, 9763, 0, tile(2654, 9809, 0)),
        down(2650, 9804, 0, tile(2641, 9764, 0)),

        // Tutorial Island Quest Guide's house.
        up(3082, 3124, 0, tile(3084, 3124, 1)),
        down(3083, 3124, 1, tile(3084, 3124, 0)),

        // Tutorial Island church.
        up(3115, 3107, 0, tile(3117, 3107, 1)),
        down(3116, 3107, 1, tile(3117, 3107, 0)),

        // Underground Pass.
        up(2304, 9915, 0, tile(2113, 4729, 1)),
        up(2336, 9793, 0, tile(2150, 4546, 1)),
        down(2112, 4729, 1, tile(2305, 9915, 0)),
        down(2150, 4545, 1, tile(2336, 9794, 0)),

        // Varrock East Bank.
        up(3255, 3421, 0, tile(3255, 3420, 1)),
        down(3255, 3421, 1, tile(3254, 3421, 0)),

        // Varrock Museum.
        up(3259, 3446, 0, tile(3259, 3449, 1)),
        down(3259, 3447, 1, tile(3259, 3445, 0)),

        // Varrock Palace.
        up(3202, 3497, 0, tile(3203, 3496, 1)),
        up(3212, 3473, 0, tile(3212, 3476, 1)),
        up(3218, 3496, 0, tile(3220, 3496, 1)),
        climb(3202, 3497, 1, up = tile(3204, 3497, 2), down = tile(3203, 3496, 0)),
        down(3212, 3474, 1, tile(3212, 3472, 0)),
        down(3219, 3496, 1, tile(3220, 3496, 0)),
        down(3203, 3497, 2, tile(3203, 3496, 1)),
        up(3221, 3472, 2, tile(3223, 3472, 3)),
        down(3222, 3472, 3, tile(3222, 3471, 2)),
        up(3202, 3472, 2, tile(3204, 3472, 3)),
        down(3203, 3472, 3, tile(3203, 3471, 2)),

        // Varrock West Bank basement.
        down(3187, 3433, 0, tile(3190, 9833, 0)),
        up(3187, 9833, 0, tile(3186, 3433, 0)),

        // Varrock West Gate.
        up(3175, 3420, 0, tile(3175, 3419, 1)),
        down(3175, 3420, 1, tile(3175, 3423, 0)),

        // Varrock church.
        up(3258, 3487, 0, tile(3258, 3486, 1)),
        climb(3258, 3487, 1, up = tile(3258, 3486, 2), down = tile(3258, 3486, 0)),
        climb(3258, 3487, 2, up = tile(3258, 3486, 3), down = tile(3258, 3486, 1)),
        down(3258, 3487, 3, tile(3258, 3486, 2)),

        // Varrock houses.
        up(3230, 3383, 0, tile(3230, 3382, 1)),
        up(3237, 3447, 0, tile(3239, 3447, 1)),
        up(3239, 3489, 0, tile(3242, 3489, 1)),
        down(3230, 3383, 1, tile(3230, 3386, 0)),
        down(3238, 3447, 1, tile(3239, 3447, 0)),
        down(3240, 3489, 1, tile(3238, 3489, 0)),

        // Varrock wall.
        up(3177, 3401, 0, tile(3176, 3401, 1)),
        down(3177, 3401, 1, tile(3180, 3401, 0)),

        // Waterfall tourist building.
        up(2516, 3424, 0, tile(2517, 3426, 1)),
        down(2516, 3425, 1, tile(2516, 3423, 0)),

        // West Ardougne church.
        up(2526, 3291, 0, tile(2527, 3293, 1)),
        up(2531, 3294, 0, tile(2531, 3293, 1)),
        down(2526, 3292, 1, tile(2526, 3290, 0)),
        down(2531, 3294, 1, tile(2530, 3294, 0)),

        // West Ardougne houses.
        up(2523, 3314, 0, tile(2523, 3313, 1)),
        up(2542, 3324, 0, tile(2543, 3326, 1)),
        down(2523, 3314, 1, tile(2523, 3317, 0)),
        up(2527, 3314, 1, tile(2527, 3313, 2)),
        up(2530, 3317, 0, tile(2529, 3317, 1)),
        down(2530, 3317, 1, tile(2530, 3320, 0)),
        down(2543, 3325, 1, tile(2544, 3325, 0)),
        down(2527, 3314, 2, tile(2527, 3317, 1)),

        // West of the Varrock lumber yard.
        up(3285, 3493, 0, offset(0, -4, 1)),
        down(3285, 3493, 1, offset(0, 4, -1)),

        // White Knights' Castle.
        up(2954, 3338, 0, tile(2956, 3338, 1)),
        down(2955, 3338, 1, tile(2955, 3337, 0)),
        up(2960, 3338, 1, tile(2959, 3339, 2)),
        up(2984, 3337, 1, tile(2984, 3340, 2)),
        up(2957, 3338, 2, tile(2956, 3339, 3)),
        down(2960, 3339, 2, tile(2960, 3340, 1)),
        down(2984, 3338, 2, tile(2984, 3336, 1)),
        down(2958, 3338, 3, tile(2957, 3340, 2)),

        // White Wolf Mountain tunnel.
        down(2820, 3484, 0, tile(2820, 9882, 0)),
        up(2820, 9883, 0, tile(2820, 3486, 0)),
        down(2876, 3480, 0, tile(2876, 9878, 0)),
        up(2876, 9880, 0, tile(2877, 3482, 0)),

        // Wilderness dungeon.
        down(3044, 3924, 0, tile(3045, 10323, 0)),
        up(3044, 10324, 0, tile(3045, 3927, 0)),

        // Wise Old Man's house, Draynor.
        up(3090, 3251, 0, tile(3093, 3251, 1)),
        down(3091, 3251, 1, tile(3089, 3251, 0)),

        // Witch's house.
        up(2906, 3469, 0, tile(2906, 3472, 1)),
        down(2906, 3470, 1, tile(2906, 3468, 0)),

        // Wizards' Tower.
        up(3103, 3159, 0, tile(3104, 3161, 1)),
        climb(3103, 3159, 1, up = tile(3104, 3161, 2), down = tile(3104, 3161, 0)),
        down(3104, 3160, 2, tile(3104, 3161, 1)),

        // Yanille West Gate.
        up(2537, 3085, 0, offset(0, -4, 1)),
        up(2537, 3096, 0, offset(0, 4, 1)),
        up(2921, 4685, 0, tile(2921, 4684, 1)),
        up(2921, 4696, 0, tile(2921, 4699, 1)),
        down(2537, 3085, 1, tile(2537, 3088, 0)),
        down(2537, 3097, 1, tile(2537, 3095, 0)),
        down(2921, 4685, 1, tile(2921, 4688, 0)),
        down(2921, 4697, 1, tile(2921, 4695, 0)),

        // Yanille Wizard Tower.
        up(2590, 3089, 0, tile(2590, 3092, 1)),
        up(2590, 3084, 1, tile(2590, 3087, 2)),
        down(2590, 3090, 1, tile(2590, 3088, 0)),
        down(2590, 3085, 2, tile(2590, 3083, 1)),

        // Yanille dungeon.
        up(2578, 9584, 0, tile(2567, 9524, 0), "You go up the stairs..."),
        down(2620, 9497, 0, tile(2620, 9566, 0)),
        up(2620, 9562, 0, tile(2621, 9496, 0)),

        // Yanille dungeon entrances.
        down(2569, 3122, 0, tile(2569, 9525, 0)),
        up(2569, 9522, 0, tile(2569, 3121, 0)),
        down(2603, 3078, 0, tile(2601, 9478, 0)),
        up(2603, 9478, 0, tile(2606, 3078, 0)),

        // Added after 2004. These land where players land in later versions of the game, or failing that in front of
        // the stairs that lead back.
        // Blast Furnace, below Keldagrim.
        up(1939, 4956, 0, tile(2931, 10196, 0)),
        down(2930, 10196, 0, tile(1940, 4958, 0)),

        // Braindeath Island. The stairs facing north land like the Falador castle courtyard ones: up beside the far
        // end, down at the foot.
        up(2137, 5088, 0, tile(2137, 5089, 1)),
        down(2137, 5088, 1, tile(2140, 5088, 0)),
        up(2149, 5088, 0, tile(2149, 5089, 1)),
        down(2149, 5088, 1, tile(2152, 5088, 0)),
        up(2163, 5088, 0, tile(2163, 5089, 1)),
        down(2163, 5088, 1, tile(2166, 5088, 0)),

        // Evil Dave's cellar, Edgeville.
        either(3076, 9893, 0, tile(3077, 3493, 0)),

        // Fenkenstrain's Castle.
        up(3537, 3551, 0, tile(3537, 3554, 1)),
        up(3559, 3551, 0, tile(3559, 3554, 1)),
        down(3537, 3552, 1, tile(3537, 3549, 0)),
        down(3559, 3552, 1, tile(3559, 3549, 0)),

        // Jiggig, down to the ogre tombs, and the stairs inside them.
        down(2485, 3042, 0, tile(2477, 9437, 2)),
        up(2478, 9437, 2, tile(2485, 3045, 0)),
        up(2443, 9417, 0, tile(2446, 9417, 2)),
        down(2443, 9417, 2, tile(2442, 9417, 0)),

        // Keldagrim.
        up(2828, 10215, 0, tile(2828, 10214, 1)),
        up(2828, 10225, 0, tile(2828, 10227, 1)),
        up(2834, 10224, 0, tile(2834, 10226, 1)),
        up(2835, 10196, 0, tile(2837, 10196, 1)),
        up(2835, 10224, 1, tile(2835, 10223, 2)),
        up(2863, 10188, 0, tile(2862, 10188, 1)),
        up(2863, 10209, 0, tile(2862, 10209, 1)),
        up(2865, 10222, 0, tile(2865, 10224, 1)),
        up(2866, 10198, 1, tile(2865, 10198, 2)),
        up(2871, 10174, 0, tile(2871, 10176, 1)),
        up(2873, 10174, 1, tile(2873, 10173, 2)),
        up(2891, 10198, 1, tile(2893, 10198, 2)),
        up(2894, 10188, 0, tile(2896, 10188, 1)),
        up(2894, 10209, 0, tile(2896, 10209, 1)),
        up(2905, 10208, 0, tile(2907, 10208, 1)),
        up(2911, 10163, 0, tile(2913, 10163, 1)),
        up(2915, 10196, 0, tile(2914, 10196, 1)),
        up(2930, 10180, 0, tile(2930, 10179, 1)),
        up(2931, 10165, 0, tile(2933, 10165, 1)),
        down(2828, 10215, 1, tile(2828, 10217, 0)),
        down(2828, 10226, 1, tile(2828, 10224, 0)),
        down(2834, 10225, 1, tile(2834, 10223, 0)),
        down(2835, 10224, 2, tile(2835, 10226, 1)),
        down(2836, 10196, 1, tile(2834, 10196, 0)),
        down(2863, 10188, 1, tile(2865, 10188, 0)),
        down(2863, 10209, 1, tile(2865, 10209, 0)),
        down(2865, 10223, 1, tile(2865, 10221, 0)),
        down(2866, 10198, 2, tile(2868, 10198, 1)),
        down(2871, 10175, 1, tile(2871, 10173, 0)),
        down(2873, 10174, 2, tile(2873, 10176, 1)),
        down(2892, 10198, 2, tile(2890, 10198, 1)),
        down(2895, 10188, 1, tile(2893, 10188, 0)),
        down(2895, 10209, 1, tile(2893, 10209, 0)),
        down(2906, 10208, 1, tile(2904, 10208, 0)),
        down(2912, 10163, 1, tile(2910, 10163, 0)),
        down(2915, 10196, 1, tile(2917, 10196, 0)),
        down(2930, 10180, 1, tile(2930, 10182, 0)),
        down(2932, 10165, 1, tile(2930, 10165, 0)),

        // Keldagrim rat pits.
        down(2914, 10188, 0, tile(1943, 4705, 1)),
        up(1942, 4704, 0, tile(1942, 4706, 1)),
        up(1943, 4703, 1, tile(2914, 10187, 0)),
        down(1942, 4705, 1, tile(1942, 4703, 0)),

        // Mage Training Arena.
        up(3357, 3306, 0, tile(3357, 3307, 1)),
        up(3367, 3306, 0, tile(3369, 3307, 1)),
        down(3357, 3306, 1, tile(3360, 3306, 0)),
        down(3367, 3306, 1, tile(3366, 3306, 0)),

        // Nardah, the mayor's house.
        up(3446, 2911, 0, tile(3449, 2911, 1)),
        down(3447, 2911, 1, tile(3445, 2911, 0)),

        // Pollnivneach.
        up(3353, 2958, 0, tile(3354, 2958, 1)),
        up(3373, 2978, 0, tile(3374, 2979, 1)),
        down(3353, 2958, 1, tile(3353, 2961, 0)),
        down(3373, 2978, 1, tile(3373, 2977, 0)),

        // Port Phasmatys.
        up(3666, 3518, 0, offset(0, 5, 1)),
        down(3666, 3520, 1, offset(0, -5, -1)),

        // Quest mansion with Ceril Carnillean and Ali Morrisane.
        up(2841, 5093, 0, tile(2841, 5096, 1)),
        up(2853, 5093, 0, tile(2853, 5096, 1)),
        down(2841, 5094, 1, tile(2841, 5092, 0)),
        down(2853, 5094, 1, tile(2853, 5092, 0)),

        // Slayer Tower.
        up(3413, 3540, 1, tile(3417, 3540, 2)),
        up(3434, 3537, 0, tile(3433, 3537, 1)),
        down(3415, 3540, 2, tile(3412, 3540, 1)),
        down(3434, 3537, 1, tile(3438, 3537, 0)),

        // Waterbirth Island dungeon, up to the island.
        either(2440, 10146, 0, tile(2523, 3740, 0)),

        // Wise Old Man's house, copy.
        up(2130, 4915, 0, tile(2133, 4915, 1)),
        down(2131, 4915, 1, tile(2129, 4915, 0))
    )
}
