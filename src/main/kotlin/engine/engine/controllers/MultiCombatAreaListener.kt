package engine.controllers

import api.predef.*
import com.google.common.collect.ImmutableSet
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.area.Area
import io.luna.game.model.map.DynamicMapSpacePool
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerAreaListener
import io.luna.net.msg.out.MultiCombatMessageWriter

/**
 * A [PlayerAreaListener] that manages entry and exit state for multi-combat areas.
 *
 * Each area is only multi-combat on its specified plane.
 *
 * @author lare96
 */
object MultiCombatAreaListener : PlayerAreaListener() {

    override fun enter(plr: Player) {
        plr.queue(MultiCombatMessageWriter(true))
    }

    override fun exit(plr: Player) {
        plr.queue(MultiCombatMessageWriter(false))
    }

    override fun computeLocatables(): ImmutableSet<Locatable> = ImmutableSet.of(
        // Eastern Wilderness
        Area.of(3136, 3520, 3327, 3647, 0),
        Area.of(3192, 3648, 3327, 3903, 0),
        Area.of(3152, 3752, 3191, 3903, 0),
        Area.of(3136, 3840, 3151, 3903, 0),

        // Wilderness volcano and castle ruins
        Area.of(3328, 3840, 3391, 3967, 0),

        // Scorpion Pit
        Area.of(3200, 3904, 3263, 3967, 0),

        // Rogues' Castle
        Area.of(3264, 3904, 3327, 3967, 0),
        Area.of(3264, 3904, 3327, 3967, 1),
        Area.of(3264, 3904, 3327, 3967, 2),

        // Rogues' Castle tower top
        Area.of(3272, 3928, 3287, 3943, 3),

        // North of the Lava Maze
        Area.of(3008, 3856, 3047, 3903, 0),
        Area.of(3048, 3864, 3055, 3871, 0),
        Area.of(3112, 3872, 3135, 3903, 0),
        Area.of(3072, 3880, 3111, 3903, 0),
        Area.of(3048, 3896, 3071, 3903, 0),

        // Chaos Temple hut (deep Wilderness)
        Area.of(2944, 3816, 2959, 3831, 0),

        // South of the Wilderness Agility Course
        Area.of(2984, 3912, 3007, 3927, 0),

        // Wilderness Bandit Camp
        Area.of(3008, 3648, 3071, 3711, 0),

        // Dark Warriors' Fortress
        Area.of(3008, 3600, 3071, 3647, 0),
        Area.of(3008, 3600, 3071, 3647, 1),
        Area.of(3008, 3600, 3071, 3647, 2),

        // Dark Warriors' Fortress spire tops
        Area.of(3016, 3616, 3023, 3647, 3),
        Area.of(3032, 3616, 3039, 3647, 3),

        // Mage Arena bank
        Area.of(2496, 4672, 2559, 4735, 0),

        // King Black Dragon's lair
        Area.of(2256, 4680, 2287, 4711, 0),

        // Abyss
        Area.of(3008, 4800, 3071, 4863, 0),

        // Varrock Sewers
        Area.of(3152, 9856, 3295, 9919, 0),

        // Barbarian Village
        Area.of(3048, 3392, 3135, 3407, 0),
        Area.of(3056, 3408, 3135, 3439, 0),
        Area.of(3064, 3440, 3135, 3447, 0),
        Area.of(3072, 3448, 3135, 3455, 0),

        // Draynor Village jail
        Area.of(3104, 3232, 3135, 3255, 0),
        Area.of(3112, 3256, 3135, 3263, 0),

        // Wizards' Tower
        Area.of(3094, 3144, 3127, 3175, 0),
        Area.of(3094, 3144, 3127, 3175, 1),
        Area.of(3094, 3144, 3127, 3175, 2),

        // Dorgesh-Kaan mine
        Area.of(3312, 9600, 3327, 9663, 0),
        Area.of(3304, 9640, 3311, 9663, 0),

        // Falador
        Area.of(2944, 3304, 3015, 3327, 0),
        Area.of(2944, 3328, 2959, 3455, 0),
        Area.of(2968, 3328, 3007, 3455, 0),
        Area.of(2960, 3336, 2967, 3455, 0),

        // Falador upper floors
        Area.of(2944, 3304, 3007, 3455, 1),
        Area.of(2944, 3304, 3007, 3455, 2),
        Area.of(2944, 3304, 3007, 3455, 3),

        // Falador Mole Lair
        Area.of(1732, 5144, 1787, 5239, 0),

        // Hammerspike's hangout
        Area.of(2962, 9806, 2975, 9819, 0),

        // Chaos Temple (Asgarnia)
        Area.of(2928, 3512, 2943, 3519, 0),

        // Burthorpe
        Area.of(2880, 3520, 2903, 3543, 0),

        // Death Plateau
        Area.of(2848, 3600, 2879, 3607, 0),

        // Trollheim
        Area.of(2896, 3688, 2919, 3695, 0),
        Area.of(2880, 3696, 2911, 3727, 0),
        Area.of(2888, 3728, 2911, 3775, 0),
        Area.of(2880, 3744, 2887, 3775, 0),

        // White Wolf Mountain
        Area.of(2816, 3456, 2879, 3519, 0),

        // Ice Queen's lair
        Area.of(2848, 9928, 2879, 9967, 0),

        // Rellekka
        Area.of(2656, 3712, 2735, 3727, 0),
        Area.of(2656, 3728, 2703, 3735, 0),
        Area.of(2712, 3728, 2735, 3735, 0),

        // Lighthouse dungeon
        Area.of(2496, 9984, 2559, 10047, 0),
        Area.of(2496, 9984, 2559, 10047, 1),

        // Lighthouse dungeon (Horror from the Deep copy)
        Area.of(2496, 4608, 2559, 4671, 0),
        Area.of(2496, 4608, 2559, 4671, 1),

        // Waterbirth Island Dungeon lower levels
        Area.of(1794, 4354, 1979, 4412),

        // Waterbirth Island Dungeon door-support room
        Area.of(2536, 10136, 2551, 10151, 0),
        Area.of(2536, 10136, 2551, 10151, 1),

        // Dagannoth Kings' lair
        Area.of(2894, 4430, 2933, 4468, 0),

        // Ranging Guild
        Area.of(2656, 3408, 2679, 3447, 0),
        Area.of(2656, 3408, 2679, 3447, 2),
        Area.of(2648, 3416, 2655, 3439, 0),
        Area.of(2648, 3416, 2655, 3439, 2),
        Area.of(2680, 3416, 2687, 3439, 0),
        Area.of(2680, 3416, 2687, 3439, 2),

        // Necromancer's tower
        Area.of(2664, 3216, 2679, 3231, 0),
        Area.of(2656, 3232, 2679, 3255, 0),
        Area.of(2656, 3232, 2679, 3255, 1),

        // Battlefield
        Area.of(2504, 3208, 2551, 3231, 0),
        Area.of(2504, 3232, 2543, 3247, 0),

        // Temple of Ikov dungeon
        Area.of(2624, 9856, 2671, 9919, 0),

        // Elemental Workshop
        Area.of(2688, 9856, 2751, 9919, 0),

        // Shadow Dungeon (Damis' cavern)
        Area.of(2728, 5064, 2751, 5111, 0),
        Area.of(2720, 5088, 2727, 5111, 0),
        Area.of(2712, 5096, 2719, 5111, 0),
        Area.of(2736, 5112, 2751, 5119, 0),

        // Castle Wars
        Area.of(2368, 3072, 2431, 3135),

        // Castle Wars tunnels
        Area.of(2368, 9472, 2431, 9535, 0),

        // Piscatoris Fishing Colony
        Area.of(2304, 3648, 2367, 3711, 0),

        // Arandar pass
        Area.of(2368, 3312, 2391, 3327, 0),

        // Jiggig
        Area.of(2456, 3032, 2495, 3055, 0),

        // Feldip Hills ogre boat beach
        Area.of(2648, 2952, 2655, 2975, 0),

        // TzHaar City and Fight Pit
        Area.of(2375, 5121, 2494, 5182, 0),

        // TzHaar Fight Cave
        Area.of(2371, 5062, 2424, 5117, 0),

        // Rashiliyia's Tomb
        Area.of(2880, 9472, 2943, 9535, 0),

        // Shaman Caves death-wing chamber
        Area.of(2800, 9288, 2815, 9311, 0),
        Area.of(2792, 9296, 2799, 9311, 0),

        // Al Kharid
        Area.of(3264, 3136, 3327, 3199, 0),
        Area.of(3264, 3136, 3327, 3199, 1),

        // Desert Bandit Camp
        Area.of(3174, 2952, 3187, 3003, 0),
        Area.of(3188, 2953, 3188, 3003, 0),
        Area.of(3173, 2954, 3173, 3005, 0),
        Area.of(3189, 2955, 3189, 3003, 0),
        Area.of(3172, 2956, 3172, 3005, 0),
        Area.of(3163, 2957, 3171, 3005, 0),
        Area.of(3190, 2957, 3190, 3003, 0),
        Area.of(3136, 2958, 3162, 3005, 0),
        Area.of(3191, 2959, 3191, 3003, 0),
        Area.of(3192, 2961, 3192, 3002, 0),
        Area.of(3193, 2963, 3193, 3001, 0),
        Area.of(3194, 2965, 3194, 2999, 0),
        Area.of(3195, 2967, 3195, 2998, 0),
        Area.of(3196, 2969, 3196, 2981, 0),
        Area.of(3197, 2971, 3197, 2980, 0),
        Area.of(3198, 2973, 3198, 2978, 0),
        Area.of(3199, 2975, 3199, 2977, 0),
        Area.of(3174, 3004, 3180, 3004, 0),
        Area.of(3174, 3005, 3177, 3005, 0),

        // Kalphite Lair
        Area.of(3456, 9472, 3519, 9535, 0),
        Area.of(3456, 9472, 3519, 9535, 2),

        // Burgh de Rott general store
        Area.of(3512, 3232, 3519, 3247, 0),
        Area.of(3512, 3232, 3519, 3247, 2),

        // The Hollows (Mort Myre Swamp)
        Area.of(3456, 3328, 3519, 3391, 0),

        // Haunted Mine (Treus Dayth's level)
        Area.of(2758, 4418, 2811, 4475, 0),

        // Pest Control island
        Area.of(2624, 2560, 2687, 2623, 0),

        // Ape Atoll
        Area.of(2688, 2688, 2815, 2815, 0),
        Area.of(2688, 2688, 2815, 2815, 1),
        Area.of(2688, 2688, 2815, 2815, 2),

        // Ape Atoll Dungeon
        Area.of(2688, 9088, 2815, 9151, 0),

        // Temple of Marimbo Dungeon
        Area.of(2752, 9152, 2815, 9215, 0),

        // Crash Island Dungeon snake pit
        Area.of(3016, 5480, 3031, 5495, 0),

        // Instances of the areas above
        InstanceArea
    )
}

/**
 * Covers every instance tile copied from a multi-combat area.
 *
 * @author TheLining
 */
private object InstanceArea : Locatable {

    // The x check comes first so real tiles never touch the world predef, which unit tests don't bind.
    override fun contains(position: Position) = position.x >= DynamicMapSpacePool.MIN_X &&
        world.dynamicMapSpacePool.getRealPosition(position)?.let { MultiCombatAreaListener.inside(it) } == true

    override fun abs(): Position = Position(x, y)

    override fun getX() = DynamicMapSpacePool.MIN_X

    override fun getY() = 0
}
