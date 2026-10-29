package api.bot.zone

import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.naturalDelay
import api.bot.Suspendable.naturalMicroDelay
import api.bot.Suspendable.waitFor
import api.bot.zone.SubZone.Companion.updateSubZone
import api.bot.zone.Zone.*
import api.predef.*
import com.google.common.collect.HashMultimap
import com.google.common.collect.ImmutableSetMultimap
import game.bot.scripts.skills.AgilityCourseTravel
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.obj.doors.BrassKeyDoor
import game.skill.runecrafting.enterAltar.Altar
import game.skill.thieving.lockedDoor.ChaosDruidTowerDoorAction
import io.luna.game.model.Position
import io.luna.game.model.area.SimpleBoxArea
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.mob.bot.Bot

/**
 * A smaller rectangular bot activity area within a broader [Zone].
 *
 * A [SubZone] gives bots a precise local area to work inside after broad travel has already chosen a parent [Zone].
 * The parent zone is still used for long-distance routing, banking fallback, and general area selection, while the
 * subzone is used for local activity decisions such as fishing, mining, woodcutting, thieving, or combat training.
 *
 * A bot can belong to several overlapping subzones. Marketplace overlays share the surrounding bank and
 * resource footprints; [updateSubZone] retains every matching subzone rather than replacing those activities.
 *
 * Each subzone has:
 * - [inside], the preferred local anchor used after the bot is inside or near the subzone.
 * - [entrance], an optional dynamic external anchor for entrances, ladders, gates, portals, levers, or boats.
 * - [area], the rectangular tile bounds used for containment checks and region indexing.
 * - [parent], the dynamic broader travel zone used for routing and fallback behavior.
 *
 * @author lare96
 */
enum class SubZone(
    val inside: Position,
    val entrance: Bot.() -> Position? = { null },
    val area: SimpleBoxArea,
    val parent: Bot.() -> Zone) {
    /**
     * Mainland bush patches from the revision 377 maps. Rimmington uses Falador's existing travel and bank parent.
     */
    CHAMPIONS_GUILD_BUSH_PATCH(
        inside = Position(3180, 3357), area = SimpleBoxArea.of(3178, 3354, 3185, 3361),
        parent = { VARROCK }),
    RIMMINGTON_BUSH_PATCH(
        inside = Position(2939, 3221), area = SimpleBoxArea.of(2937, 3218, 2944, 3225),
        parent = { FALADOR }),
    ARDOUGNE_BUSH_PATCH(
        inside = Position(2616, 3225), area = SimpleBoxArea.of(2614, 3222, 2621, 3229),
        parent = { ARDOUGNE }),

    /**
     * Native cactus, bittercap and belladonna plots reached through shared webwalking and travel fallback.
     */
    AL_KHARID_CACTUS_PATCH(
        inside = Position(3314, 3202), area = SimpleBoxArea.of(3312, 3199, 3319, 3206),
        parent = { AL_KHARID }),
    CANIFIS_MUSHROOM_PATCH(
        inside = Position(3450, 3472), area = SimpleBoxArea.of(3448, 3469, 3455, 3476),
        parent = { CANIFIS }),
    DRAYNOR_BELLADONNA_PATCH(
        inside = Position(3085, 3354), area = SimpleBoxArea.of(3083, 3351, 3090, 3358),
        parent = { DRAYNOR }),

    /**
     * Native revision 377 tree patch locations; arrival anchors stand beside their multi-tile tree objects.
     */
    TAVERLEY_TREE_PATCH(
        inside = Position(2934, 3437), area = SimpleBoxArea.of(2932, 3434, 2940, 3442),
        parent = { TAVERLY }),
    FALADOR_TREE_PATCH(
        inside = Position(3002, 3372), area = SimpleBoxArea.of(3000, 3369, 3008, 3377),
        parent = { FALADOR }),
    VARROCK_TREE_PATCH(
        inside = Position(3227, 3458), area = SimpleBoxArea.of(3225, 3455, 3233, 3463),
        parent = { VARROCK }),
    LUMBRIDGE_TREE_PATCH(
        inside = Position(3191, 3230), area = SimpleBoxArea.of(3189, 3227, 3197, 3235),
        parent = { LUMBRIDGE }),

    /**
     * Fruit-tree plots with existing travel parents. Shared webwalking handles approach to the actual tree.
     */
    GNOME_STRONGHOLD_FRUIT_PATCH(
        inside = Position(2474, 3445), area = SimpleBoxArea.of(2472, 3442, 2480, 3450),
        parent = { GNOME_STRONGHOLD }),
    BRIMHAVEN_FRUIT_PATCH(
        inside = Position(2763, 3212), area = SimpleBoxArea.of(2761, 3209, 2769, 3217),
        parent = { KARAMJA }),
    CATHERBY_FRUIT_PATCH(
        inside = Position(2859, 3433), area = SimpleBoxArea.of(2857, 3430, 2865, 3438),
        parent = { CATHERBY }),

    /**
     * Regular farming plots mapped in revision 377 and Lost City; anchors use the paths between plots.
     */
    FALADOR_FARM(
        inside = Position(3055, 3309), area = SimpleBoxArea.of(3047, 3300, 3063, 3315),
        parent = { FALADOR }),
    CATHERBY_FARM(
        inside = Position(2811, 3465), area = SimpleBoxArea.of(2802, 3456, 2818, 3471),
        parent = { CATHERBY }),
    ARDOUGNE_FARM(
        inside = Position(2668, 3376), area = SimpleBoxArea.of(2659, 3367, 2675, 3382),
        parent = { ARDOUGNE }),

    /**
     * Hops fields use the same native patch flow, with seed counts supplied by the chosen crop.
     */
    YANILLE_HOPS(
        inside = Position(2573, 3104), area = SimpleBoxArea.of(2571, 3100, 2580, 3109),
        parent = { YANILLE }),
    ENTRANA_HOPS(
        inside = Position(2808, 3336), area = SimpleBoxArea.of(2806, 3332, 2815, 3341),
        parent = { ENTRANA }),
    LUMBRIDGE_HOPS(
        inside = Position(3226, 3315), area = SimpleBoxArea.of(3224, 3310, 3234, 3320),
        parent = { LUMBRIDGE }),
    MCGRUBORS_HOPS(
        inside = Position(2663, 3525), area = SimpleBoxArea.of(2661, 3520, 2672, 3531),
        parent = { SEERS_VILLAGE }),

    /**
     * Turael's Burthorpe house, used for native Slayer assignment dialogue.
     */
    TURAEL(
        inside = Position(2931, 3536),
        area = SimpleBoxArea.ofRadius(2927, 3533, 9, 8),
        parent = { BURTHORPE }),

    /**
     * Fountain room below the Heroes' Guild, used to restore glory charges. Shared travel handles its entrance.
     */
    HEROES_GUILD_FOUNTAIN(
        inside = Position(2916, 9893),
        area = SimpleBoxArea.of(2913, 9890, 2922, 9899),
        parent = { TAVERLY }),

    /**
     * Supplied Canifis town footprint containing Mazchna's configured spawn.
     */
    CANIFIS_MAIN(
        inside = Position(3494, 3473),
        area = SimpleBoxArea.ofRadius(3473, 3461, 47, 57),
        parent = { CANIFIS }),

    /**
     * Supplied Edgeville Dungeon footprint; the arrival tile is Vannaka's configured spawn.
     * Existing webwalking and travel fallback handle the dungeon transition.
     */
    EDGEVILLE_DUNGEON(
        inside = Position(3147, 9914),
        area = SimpleBoxArea.ofRadius(3075, 9816, 77, 185),
        parent = { EDGEVILLE }),

    /**
     * Supplied Zanaris town footprint containing Chaeldar's configured spawn.
     */
    ZANARIS_MAIN(
        inside = Position(2387, 4455),
        area = SimpleBoxArea.ofRadius(2314, 4351, 197, 135),
        parent = { ZANARIS }),

    /**
     * Supplied Shilo Village footprint and arrival tile, shared by local gameplay activities.
     * Duradel is upstairs within this footprint; the webwalker handles his approach.
     */
    SHILO_VILLAGE(
        inside = Position(2852, 2958),
        area = SimpleBoxArea.ofRadius(2816, 2944, 64, 64),
        parent = { KARAMJA }),

    /**
     * Yanille's sandpit at (2542, 3103), used to fill owned buckets for glassmaking.
     */
    YANILLE_SAND_PIT(
        inside = Position(2541, 3103),
        area = SimpleBoxArea.of(2538, 3099, 2549, 3109),
        parent = { YANILLE }),

    /**
     * Fishing Guild docks and bank. All native moving-spot destinations are enclosed; entry requires Fishing 68.
     */
    FISHING_GUILD(
        inside = Position(2586, 3418),
        area = SimpleBoxArea.of(2578, 3380, 2620, 3430),
        parent = { ARDOUGNE }),

    /**
     * Stronghold river fishing and the nearby bank. Uses the supplied Stronghold bank tile for arrival.
     */
    GNOME_STRONGHOLD_FISHING(
        inside = Position(2447, 3424),
        area = SimpleBoxArea.of(2375, 3400, 2450, 3433),
        parent = { GNOME_STRONGHOLD }),

    /**
     * Seers' northern fishing spots and bank, using the existing town arrival anchor.
     */
    SEERS_VILLAGE_FISHING(
        inside = SEERS_VILLAGE.anchor,
        area = SimpleBoxArea.of(2708, 3484, 2736, 3538),
        parent = { SEERS_VILLAGE }),

    /**
     * Fishing on the Ardougne river north of town. Arrival is an existing north-bank anchor.
     */
    ARDOUGNE_RIVER_FISHING(
        inside = Position(2618, 3331),
        area = SimpleBoxArea.of(2556, 3328, 2624, 3378),
        parent = { ARDOUGNE }),

    /**
     * Dairy cow at (3040, 3301) on the farm south of Falador, used to fill empty buckets with milk.
     */
    FALADOR_DAIRY_COW(
        inside = Position(3039, 3301),
        area = SimpleBoxArea.of(3035, 3297, 3045, 3306),
        parent = { FALADOR }),

    /**
     * Marketplace session destinations. Exact work rectangles are defined in BotMarketplace; these
     * enclosing footprints retain nearby banking/resource memberships and use parent anchors for arrival.
     */
    VARROCK_MARKETPLACE(
        inside = VARROCK.anchor,
        area = SimpleBoxArea.of(3173, 3427, 3197, 3454), parent = { VARROCK }),
    EDGEVILLE_MARKETPLACE(
        inside = EDGEVILLE.anchor,
        area = SimpleBoxArea.of(3086, 3485, 3098, 3501), parent = { EDGEVILLE }),
    SEERS_VILLAGE_MARKETPLACE(
        inside = SEERS_VILLAGE.anchor,
        area = SimpleBoxArea.of(2720, 3484, 2729, 3493), parent = { SEERS_VILLAGE }),
    AL_KHARID_MARKETPLACE(
        inside = AL_KHARID.anchor,
        area = SimpleBoxArea.ofRadius(3269, 3161, 9, 13), parent = { AL_KHARID }),

    /**
     * Native Party Room floor and balloon footprint, using Seers Village travel and banking fallback.
     */
    PARTY_ROOM(
        inside = Position(2736, 3475),
        area = SimpleBoxArea.of(2730, 3463, 2744, 3476), parent = { SEERS_VILLAGE }),

    /**
     * Full Gnome course footprint, including the upper platforms used by native obstacles.
     */
    GNOME_STRONGHOLD_AGILITY(
        inside = Position(2474, 3437),
        area = SimpleBoxArea.ofRadius(2467, 3413, 25, 29),
        parent = { GNOME_STRONGHOLD }),

    /**
     * Central playable lane of the native Gnome Ball pitch. The native area listener owns admission and
     * possession; the shared travel layer reaches the gate/pitch and retains its existing fallback.
     */
    GNOME_BALL_PITCH(
        inside = Position(2389, 3488),
        area = SimpleBoxArea.of(2384, 3484, 2407, 3492),
        parent = { GNOME_STRONGHOLD }),

    /**
     * Barbarian course including its upper platform, entered through the northern pipe.
     */
    BARBARIAN_OUTPOST_AGILITY(
        inside = Position(2551, 3554),
        entrance = { Position(2552, 3561) },
        area = SimpleBoxArea.of(2530, 3540, 2555, 3558),
        parent = { SEERS_VILLAGE }),

    /**
     * Wilderness course north of the entrance ridge. Failure pits are recovered by the script.
     */
    WILDERNESS_AGILITY(
        inside = Position(2998, 3933),
        entrance = { Position(2998, 3916) },
        area = SimpleBoxArea.of(2990, 3933, 3008, 3968),
        parent = { WILDERNESS }),

    /** Water-obelisk activity area, with Catherby banking and shared travel fallback. */
    WATER_OBELISK(
        inside = Position(2844, 3425),
        area = SimpleBoxArea.ofRadius(2829, 3415, 35, 20),
        parent = { CATHERBY }),

    /** Fire-obelisk activity area, using Taverly and the shared travel/banking fallback. */
    FIRE_OBELISK(
        inside = Position(2821, 9827),
        area = SimpleBoxArea.ofRadius(2814, 9824, 9, 9),
        parent = { TAVERLY }),

    /** Air-obelisk activity area, with Edgeville banking and shared travel fallback. */
    AIR_OBELISK(
        inside = Position(3089, 3571),
        area = SimpleBoxArea.ofRadius(3082, 3564, 12, 13),
        parent = { EDGEVILLE }),

    /** Earth-obelisk activity area, with Edgeville banking and shared travel fallback. */
    EARTH_OBELISK(
        inside = Position(3087, 9935),
        area = SimpleBoxArea.ofRadius(3081, 9929, 13, 15),
        parent = { EDGEVILLE }),

    HAM_CULT(
        inside = Position(3149, 9652),
        entrance = { Position(3167, 3246) },
        area = SimpleBoxArea.of(3135, 9606, 3189, 9660),
        parent = { LUMBRIDGE }),
    LUMBRIDGE_CHICKEN_COOP(
        inside = Position(3233, 3294),
        area = SimpleBoxArea.of(3225, 3287, 3236, 3301),
        parent = { LUMBRIDGE }),
    ICE_MOUNTAIN(
        inside = Position(3025, 3462),
        area = SimpleBoxArea.of(2990, 3442, 3033, 3542),
        parent = { EDGEVILLE }),
    DRAYNOR_SEWERS(
        inside = Position(3117, 9645),
        entrance = { Position(3085, 3271) },
        area = SimpleBoxArea.of(3077, 9641, 3126, 9697),
        parent = { DRAYNOR }),
    VARROCK_SEWERS(
        inside = Position(3229, 9904),
        entrance = { Position(3238, 3458) },
        area = SimpleBoxArea.of(3152, 9870, 3245, 9919),
        parent = { VARROCK }),

    //todo SKELETAL_WYVERNS(inside = Position())3060, 9553 |3022, 9537, 3071, 9558
    KARAMJA_DUNGEON(
        inside = Position(2856, 9570),
        entrance = { Position(2857, 3166) },
        area = SimpleBoxArea.of(2823, 9539, 2906, 9661),
        parent = { KARAMJA }),

    WHITE_KNIGHTS_CASTLE(
        inside = Position(2971, 3343),
        area = SimpleBoxArea.of(2965, 3337, 2979, 3347),
        parent = { FALADOR }),

    /**
     * The area east of Draynor where the trapdoor is located. Intended for money-making yew/low-level woodcutters
     * and low-level combat bots.
     * - 3 yew trees
     * - Normal trees
     * - Oak trees
     * - Low level goblins
     */
    EAST_DRAYNOR_YEWS(
        inside = Position(3166, 3238),
        area = SimpleBoxArea.of(3138, 3202, 3197, 3259),
        parent = { DRAYNOR }),

    /**
     * The Al-Kharid palace. Used for low-level combat training.
     */
    AL_KHARID_PALACE(
        inside = Position(3292, 3176),
        area = SimpleBoxArea.of(3282, 3159, 3303, 3177),
        parent = { AL_KHARID }),

    /**
     * Iorwerth camp and its loom in Tirannwn.
     */
    IORWERTH_CAMP(
        inside = Position(2204, 3253),
        area = SimpleBoxArea.ofRadius(2184, 3239, 30, 27),
        parent = { TIRANNWN }),

    /** The loom south of Falador, reached through the existing zone travel fallback. */
    SOUTH_FALADOR_LOOM(
        inside = Position(3039, 3288),
        area = SimpleBoxArea.ofRadius(3038, 3285, 3, 4),
        parent = { FALADOR }),

    /** The Edgeville monastery, used for low-level to mid-level combat training. */
    EDGEVILLE_MONASTERY(
        inside = Position(3051, 3482),
        area = SimpleBoxArea.of(3041, 3480, 3062, 3509),
        parent = { EDGEVILLE }),

    /**
     * The edgeville dark wizards' area. Used for mid-level combat training.
     */
    VARROCK_DARK_WIZARDS(
        inside = Position(3292, 3418),
        area = SimpleBoxArea.of(3218, 3362, 3237, 3378),
        parent = { VARROCK }),

    /**
     * The Falador west mine. Intended for low -> mid level miners.
     * - 2 copper
     * - 6 tin
     * - 3 iron
     * - 2 coal rocks
     */
    FALADOR_WEST_MINE(
        inside = Position(2907, 3360),
        area = SimpleBoxArea.of(2901, 3351, 2912, 3368),
        parent = { FALADOR }),

    /**
     * The Dwarven mine under Falador. Intended for low -> high level miners.
     * - 5 clay
     * - 11 copper
     * - 10 tin
     * - 9 iron
     * - 11 coal
     * - 2 gold
     * - 2 mithril
     * - 3 adamant
     */
    DWARVEN_MINE(
        inside = Position(3058, 9776),
        area = SimpleBoxArea.ofRadius(2959, 9753, 110, 104),
        parent = { FALADOR }),

    /**
     * Original revision 377 Mining Guild chamber: 37 coal and 5 mithril rocks, requiring Mining 60.
     * Bounds cover the guild ladder and chamber below Falador, south of the Dwarven Mine door at 3046,9756.
     */
    MINING_GUILD(
        inside = Position(3053, 9743),
        area = SimpleBoxArea.of(3016, 9732, 3055, 9756),
        parent = { FALADOR }),

    /**
     * Ground-floor Crafting Guild mine: six clay, six silver and seven gold rocks in the 377 reference map.
     * The existing guild door crossing owns entry; miners prepare the required apron before travelling.
     */
    CRAFTING_GUILD_MINE(
        inside = Position(2940, 3287),
        area = SimpleBoxArea.of(2936, 3275, 2944, 3292),
        parent = { FALADOR }),

    // TODO rimmington mine

    /**
     * The wizards' tower area. Intended for low-level combat training.
     * - Low level wizards
     */
    WIZARDS_TOWER(
        inside = Position(3113, 3195),
        area = SimpleBoxArea.of(3090, 3143, 3127, 3177),
        parent = { DRAYNOR }),

    /**
     * The home area, currently Varrock west bank. Intended for smithers, pickpockets, dumb cookers, and low -> average
     * intelligence firemakers.
     */
    HOME(
        inside = VARROCK.anchor,
        area = SimpleBoxArea.of(3171, 3425, 3198, 3451),
        parent = { VARROCK }),

    /**
     * The main varrock square containing the fountain water source.
     */
    VARROCK_SQUARE(
        inside = Position(3207, 3428),
        area = SimpleBoxArea.ofRadius(3205, 3421, 20, 18),
        parent = { VARROCK }),

    /**
     * The deep wilderness area where the 10-coin chest is contained.
     */
    TEN_COIN_CHEST_HUT(
        inside = Position(3190, 3959),
        area = SimpleBoxArea.of(3184, 3953, 3198, 3965),
        parent = { WILDERNESS }),

    /**
     * The room with the steel arrowtip chest in Hemenster, south-west of seers' village.
     */
    HEMENSTER_CHEST_ROOM(
        inside = Position(2642, 3447),
        area = SimpleBoxArea.of(2625, 3410, 2643, 3455),
        parent = { SEERS_VILLAGE }),

    /**
     * The ground-floor room of the chaos druid tower north of Ardougne, behind a door that is picked from outside.
     */
    CHAOS_DRUID_TOWER(
        inside = Position(2563, 3356),
        area = SimpleBoxArea.of(2561, 3355, 2564, 3357),
        parent = { ARDOUGNE }),

    /**
     * The southern chaos druids in Yanille dungeon, reached from the stairs south of the bank.
     * Includes the entrance landing and exit stairs without crossing the deeper dungeon obstacles.
     */
    YANILLE_DUNGEON_CHAOS_DRUIDS(
        inside = Position(2611, 9485),
        entrance = { Position(2606, 3078) },
        area = SimpleBoxArea.ofRadius(2595, 9475, 27, 26),
        parent = { YANILLE }),

    /**
     * Chaos druids in the Wilderness section of Edgeville Dungeon.
     */
    EDGEVILLE_DUNGEON_CHAOS_DRUIDS(
        inside = Position(3110, 9937),
        entrance = { Position(3096, 3468) },
        area = SimpleBoxArea.ofRadius(3102, 9922, 24, 23),
        parent = { EDGEVILLE }),

    /**
     * Chaos druids in the eastern part of Taverley Dungeon, before the dusty-key door.
     * The entrance ladder is at (2884, 3397), paired with the ladder at (2884, 9797).
     */
    TAVERLEY_DUNGEON_CHAOS_DRUIDS(
        inside = Position(2933, 9849),
        entrance = { Position(2885, 3397) },
        area = SimpleBoxArea.ofRadius(2926, 9843, 15, 13),
        parent = { TAVERLY }),

    /**
     * The rock crabs north of Rellekka. Intended for intelligent mid-level combat training.
     */
    ROCK_CRABS(
        inside = Position(2670, 3715),
        area = SimpleBoxArea.of(2648, 3711, 2687, 3742),
        parent = { RELLEKKA }),

    /**
     * The walled mine south of the Rellekka rock crabs, entered through the gate at (2681, 3697). Intended for
     * low -> mid level miners.
     * - 4 clay rocks
     * - 3 silver rocks
     * - 7 coal rocks
     */
    RELLEKKA_MINE(
        inside = Position(2683, 3700),
        area = SimpleBoxArea.of(2679, 3698, 2688, 3710),
        parent = { RELLEKKA }),

    /**
     * The chaos druid tower dungeon.
     * - Ogres
     * - Blood rune thieving chest
     */
    CHAOS_DRUID_TOWER_DUNGEON(
        inside = Position(2565, 9753),
        entrance = { Position(2563, 3356) },
        area = SimpleBoxArea.of(2559, 9729, 2594, 9761),
        parent = { ARDOUGNE }),

    /**
     * The Varrock east-bank. Contains ranges nearby which make it a valid cooking area.
     */
    VARROCK_EAST_BANK(
        inside = Position(3253, 3427),
        area = SimpleBoxArea.of(3234, 3400, 3261, 3428),
        parent = { VARROCK }),

    /**
     * The rune essence mine. Intended for low-level and low-confidence money makers.
     */
    ESSENCE_MINE(
        inside = Position(2910, 4832),
        entrance = { Position(3253, 3401) },
        area = SimpleBoxArea.of(2882, 4805, 2939, 4860),
        parent = { VARROCK }),

    /**
     * The Draynor bank and surrounding areas. Intended mainly for low-level fishers and dexterous/low-level
     * woodcutters. Low-level dumb bots may also train combat here.
     * - Bank booths
     * - 5 willow trees
     * - Normal trees
     * - Oak trees
     * - Net and bait fishing spots
     * - Guards at the jail
     */
    DRAYNOR_MAIN(
        inside = DRAYNOR.anchor,
        area = SimpleBoxArea.of(3073, 3221, 3134, 3262),
        parent =
            { DRAYNOR }),

    /**
     * A low-level mining area near Varrock. Intended for low-level miners.
     * - 3 clay rocks
     * - 3 iron rocks
     * - 3 silver rocks
     * - 8 tin rocks
     */
    VARROCK_SW_MINE(
        inside = Position(3187, 3373),
        area = SimpleBoxArea.of(3166, 3360, 3187, 3380),
        parent =
            { VARROCK }),
    //todo taverly druids circle, level 30+ spot(2926,3490)
    /**
     * A low-level mining area near Varrock. Intended for low-level miners.
     * - 9 copper rocks
     * - 6 tin rocks
     * - 4 iron rocks
     */
    VARROCK_SE_MINE(
        inside = Position(3284, 3372),
        area = SimpleBoxArea.of(3274, 3354, 3300, 3376),
        parent =
            { VARROCK }),

    /**
     * The yew trees around the Lumber Yard north-east of Varrock. Intended for low-level and money making woodcutters.
     * - 3 yew trees
     * - Normal trees
     * - Oak trees
     */
    LUMBER_YARD_YEWS(
        inside = Position(3287, 3459),
        area = SimpleBoxArea.of(3265, 3457, 3309, 3513),
        parent =
            { VARROCK }),

    /**
     * The Rogues Den area. Intended for the best cooking training and profit.
     */
    ROGUES_DEN(
        inside = Position(3045, 4972, 1),
        entrance = { Position(2906, 3537) },
        area = SimpleBoxArea.of(3036, 4961, 3067, 4988, 1),
        parent = { BURTHORPE }),

    /**
     * The Edgeville Dungeon mine. Intended for low -> high level money-making miners. They will use the brass key.
     * - 2 copper rocks
     * - 2 tin rocks
     * - 3 iron rocks
     * - 3 silver rocks
     * - 6 coal rocks
     * - 1 mithril rock
     * - 2 adamant rocks
     */
    EDGEVILLE_DUNGEON_MINE(
        inside = Position(3137, 9875),
        entrance = { Position(3115, 3448) },
        area = SimpleBoxArea.of(3134, 9867, 3143, 9880),
        parent = { VARROCK }),

    /**
     * The Edgeville Dungeon hill giants area. Intended for mid -> high level combat training.
     */
    EDGEVILLE_DUNGEON_HILL_GIANTS(
        inside = Position(3117, 9849),
        entrance = EDGEVILLE_DUNGEON_MINE.entrance,
        area = SimpleBoxArea.of(3090, 9820, 3128, 9855),
        parent = { VARROCK }),

    /**
     * The yew trees north of Varrock Palace. Intended for money making woodcutters.
     * - 3 yew trees
     */
    VARROCK_PALACE_YEWS(
        inside = Position(3213, 3501),
        area = SimpleBoxArea.of(3201, 3496, 3224, 3508),
        parent =
            { VARROCK }),

    /**
     * The yew trees south of Falador. Intended for low-level and money making woodcutters.
     * - 3 yew trees
     * - Oak trees
     * - Normal trees
     */
    SOUTH_FALADOR_YEWS(
        inside = Position(3008, 3314),
        area = SimpleBoxArea.of(2991, 3303, 3043, 3327),
        parent =
            { FALADOR }),

    /**
     * The farm south of Falador. Has a water source, cabbage patch, loom, cows, and chickens.
     */
    SOUTH_FALADOR_FARM(
        inside = Position(3008, 3314), // todo add to combat and harvesting
        area = SimpleBoxArea.ofRadius(3012, 3279, 58, 37),
        parent =
            { FALADOR }),

    /**
     * The main Edgeville area including the bank and water sources.
     */
    EDGEVILLE_MAIN(
        inside = Position(3087, 3494),
        area = SimpleBoxArea.ofRadius(3074, 3486, 29, 20),
        parent =
            { EDGEVILLE }),

    /**
     * The Lumbridge river area. Intended for bait drop-fishers and low-level combat.
     * - Goblins
     * - 2 bait fishing spots
     */
    LUMBRIDGE_RIVER(
        inside = Position(3249, 3228),
        // Wide enough that the target search, a square around the centre, reaches the fishing spot
        // tiles at 3238,3254 and 3238,3255 without overlapping the cow pen.
        area = SimpleBoxArea.of(3234, 3227, 3265, 3251),
        parent =
            { LUMBRIDGE }),

    /**
     * The south Lumbridge mine. Intended for smart money-making miners.
     * - 7 coal rocks
     * - 5 mithril rocks
     * - 2 adamant rocks
     */
    SOUTH_LUMBRIDGE_MINE(
        inside = Position(3241, 3165),
        area = SimpleBoxArea.of(3231, 3155, 3245, 3169),
        parent = { LUMBRIDGE }),

    /**
     * The musa point fishing area. Intended for mid-level fishers.
     */
    MUSA_POINT_FISHING(
        inside = Position(2924, 3173),
        area = SimpleBoxArea.of(2922, 3173, 2928, 3181),
        parent = { KARAMJA }),

    /**
     * The Lumbridge swamp. Intended for low-level combat training.
     */
    LUMBRIDGE_SWAMP(
        inside = Position(3229, 3171),
        area = SimpleBoxArea.of(3203, 3168, 3231, 3193),
        parent = { LUMBRIDGE }),

    /**
     * Also known as the Scorpion Mine, this is a major mining area north of Al Kharid.
     * - 3 copper rocks
     * - 1 tin rock
     * - 9 iron rocks
     * - 5 silver rocks
     * - 3 coal rocks
     * - 2 gold rocks
     * - 2 mithril rocks
     * - 2 adamant rocks
     */
    AL_KHARID_MINE(
        inside = Position(3298, 3273),
        area = SimpleBoxArea.of(3288, 3271, 3312, 3324),
        parent =
            { AL_KHARID }),

    /**
     * The flax field south-east of Seers' Village. Intended for money-makers.
     * - flax
     * - spinning wheel
     */
    SOUTH_SEERS_VILLAGE_FLAX(
        inside = Position(2735, 3441),
        area = SimpleBoxArea.of(2734, 3436, 2751, 3453),
        parent =
            { SEERS_VILLAGE }),

    /**
     * The wheat field beside the Lumbridge windmill. Intended for flour millers.
     * - wheat
     * - flour mill
     */
    LUMBRIDGE_MILL_WHEAT(
        inside = Position(3158, 3298),
        area = SimpleBoxArea.of(3152, 3288, 3163, 3306),
        parent = { LUMBRIDGE }),

    /**
     * The wheat field south of the windmill north of Ardougne. Intended for flour millers.
     * - wheat
     * - flour mill
     */
    ARDOUGNE_MILL_WHEAT(
        inside = Position(2647, 3349),
        area = SimpleBoxArea.of(2640, 3345, 2655, 3353),
        parent = { ARDOUGNE }),

    /**
     * The yew trees south of Seers' Village. Intended for money-makers and low -> high level dumb woodcutters.
     * - 3 yew trees
     * - Normal trees
     * - Oak trees
     * - Maple trees
     */
    SOUTH_SEERS_VILLAGE_YEWS(
        inside = Position(2721, 3460),
        area = SimpleBoxArea.of(2688, 3456, 2750, 3476),
        parent =
            { CATHERBY }),

    /**
     * The main bank and trees near Seers' Village. Intended for mid-level woodcutters.
     * - 4 maple trees
     * - 6 willow trees
     * - Normal trees
     */
    SEERS_VILLAGE_MAIN(
        inside = Position(2723, 3504),
        area = SimpleBoxArea.of(2688, 3478, 2740, 3514),
        parent =
            { SEERS_VILLAGE }),

    /**
     * The yew trees west of Catherby. Intended for intelligent money-makers.
     * - 6 yew trees
     * - Willow trees
     * - Normal trees
     */
    WEST_CATHERBY_YEWS(
        inside = Position(2763, 3429),
        area = SimpleBoxArea.of(2750, 3419, 2774, 3434),
        parent =
            { CATHERBY }),

    /**
     * The Catherby fishing shore. Intended for low -> mid-range level fishing trainers and money-makers.
     * - Net and big net fishing
     * - Harpoon fishing
     * - Lobster pot fishing
     */
    EAST_CATHERBY_FISHING(
        inside = Position(2838, 3435),
        area = SimpleBoxArea.ofRadius(2829, 3415, 35, 20),
        parent =
            { CATHERBY }),

    /**
     * The Piscatoris fishing colony. Intended for the highest level fishers.
     */
    PISCATORIS_FISHING_COLONY_MAIN(
        inside = PISCATORIS_FISHING_COLONY.anchor,
        area = SimpleBoxArea.of(2306, 3663, 2363, 3705),
        parent = { PISCATORIS_FISHING_COLONY }),

    /**
     * The karambwan spots on northeast Karamja, north of the shipyard.
     */
    KARAMJA_KARAMBWAN_FISHING(
        inside = Position(2905, 3117),
        area = SimpleBoxArea.of(2895, 3112, 2916, 3121),
        parent = { KARAMJA }),

    /**
     * The warrior women west of Ardougne market. Intended for level 25-39 pickpocket training.
     * Covers both warrior-woman spawns and their five-tile wander range.
     */
    ARDOUGNE_WARRIOR_THIEVING(
        inside = Position(2629, 3295),
        area = SimpleBoxArea.of(2624, 3290, 2637, 3304),
        parent = { ARDOUGNE }),

    /**
     * Upper Watchtower floor containing Yanille's four pickpocketable Watchmen and their wander range.
     * Arrival uses an existing Watchman spawn tile; shared travel owns tower access.
     */
    YANILLE_WATCHMEN(
        inside = Position(2549, 3112, 1),
        area = SimpleBoxArea.of(2539, 3107, 2554, 3121, 1),
        parent = { YANILLE }),

    /**
     * Ground-floor Gnomes west of the Stronghold bank. Bounds include the local civilian spawns and wander range,
     * while the plane restriction excludes upstairs residents. No quest or special equipment is required.
     */
    GNOME_STRONGHOLD_THIEVING(
        inside = Position(2423, 3426),
        area = SimpleBoxArea.of(2388, 3412, 2432, 3454),
        parent = { GNOME_STRONGHOLD }),

    /**
     * The Ardougne market square. Intended for dexterous thieving trainers and low -> mid-range level money-makers.
     * - Paladins
     * - Guards
     * - Silk stall
     * - Baker's stall
     * - Fur stall
     * - Gem stall
     */
    ARDOUGNE_SQUARE_THIEVING(
        inside = Position(2661, 3306),
        area = SimpleBoxArea.of(2645, 3290, 2677, 3325),
        parent =
            { ARDOUGNE }),

    /**
     * The south-east Ardougne mine. Intended for dexterous mining trainers.
     * - 13 iron rocks
     * - 4 coal rocks
     */
    SOUTH_EAST_ARDOUGNE_MINE(
        inside = Position(2598, 3229),
        area = SimpleBoxArea.of(2579, 3215, 2625, 3240),
        parent =
            { ARDOUGNE }),

    /**
     * The Legends' Guild mine, also known as the East Ardougne Mine. Intended for dexterous mining trainers and
     * intelligent money-makers.
     * - 11 iron rocks
     * - 13 coal rocks
     */
    LEGENDS_GUILD_MINE(
        inside = Position(2702, 3332),
        area = SimpleBoxArea.of(2688, 3326, 2717, 3340),
        parent =
            { ARDOUGNE }),

    /**
     * The farming patch located north of Lumbridge. Intended for farmers and low-level thieving trainers.
     * - Hops patch
     * - Level 7 farmer (pickpocketing)
     */
    NORTH_LUMBRIDGE_FARMING(
        inside = Position(3229, 3309),
        area = SimpleBoxArea.of(3223, 3309, 3242, 3321),
        parent =
            { LUMBRIDGE }),

    /**
     * The iconic Lumbridge courtyard. Intended for very low-level thieving trainers.
     * - Various men and women
     * - Hans
     */
    LUMBRIDGE_COURT_YARD(
        inside = LUMBRIDGE.anchor,
        area = SimpleBoxArea.of(3215, 3205, 3230, 3229),
        parent =
            { LUMBRIDGE }),

    /**
     * A four-story tower south of Seers' Village.
     * - 4 magic trees
     * - Normal trees
     */
    SORCERERS_TOWER_MAGICS(
        inside = Position(2702, 3391),
        area = SimpleBoxArea.of(2692, 3389, 2717, 3418),
        parent =
            { SEERS_VILLAGE }),

    /**
     * The magic trees between the Sorcerer's Tower and the yews south of Seers' Village. Intended for high-level
     * woodcutters and money-makers.
     * - 3 magic trees
     * - Normal trees
     * - Oak trees
     */
    SOUTH_SEERS_VILLAGE_MAGICS(
        inside = Position(2691, 3426),
        area = SimpleBoxArea.of(2685, 3419, 2710, 3435),
        parent =
            { SEERS_VILLAGE }),

    /**
     * The maple trees south of the Sorcerer's Tower. Intended for mid-level woodcutters and money-makers.
     * - 6 maple trees
     * - Normal trees
     * - Oak trees
     */
    SOUTH_SORCERERS_TOWER_MAPLES(
        inside = Position(2713, 3376),
        area = SimpleBoxArea.of(2700, 3360, 2720, 3388),
        parent =
            { SEERS_VILLAGE }),

    /**
     * The maple trees north-west of Seers' Village. Intended for mid-level woodcutters and money-makers.
     * - 25 maple trees
     * - 5 willow trees
     * - Normal trees
     */
    NORTH_WEST_SEERS_VILLAGE_MAPLES(
        inside = Position(2668, 3531),
        area = SimpleBoxArea.of(2648, 3515, 2701, 3546),
        parent =
            { SEERS_VILLAGE }),

    /**
     * The maple forest north of Seers' Village. Intended for mid -> high level woodcutters and money-makers.
     * - 43 maple trees
     * - Normal trees
     * - Oak trees
     */
    NORTH_SEERS_VILLAGE_MAPLES(
        inside = Position(2707, 3563),
        area = SimpleBoxArea.of(2660, 3547, 2735, 3592),
        parent =
            { SEERS_VILLAGE }),

    /**
     * The willow trees on the beach west of the Catherby bank. Intended for dexterous willow trainers.
     * - 3 willow trees
     */
    CATHERBY_BEACH_WILLOWS(
        inside = Position(2783, 3428),
        area = SimpleBoxArea.of(2775, 3418, 2800, 3434),
        parent =
            { CATHERBY }),

    /**
     * The trees north of Catherby. Intended for low-level woodcutters.
     * - Normal trees
     * - Oak trees
     */
    NORTH_CATHERBY_TREES(
        inside = Position(2783, 3459),
        area = SimpleBoxArea.of(2760, 3436, 2805, 3480),
        parent =
            { CATHERBY }),

    /**
     * The willow trees south of Ardougne. Intended for mid-level woodcutters.
     * - 13 willow trees
     * - Normal trees
     * - Oak trees
     */
    SOUTH_ARDOUGNE_WILLOWS(
        inside = Position(2654, 3241),
        area = SimpleBoxArea.of(2626, 3220, 2680, 3262),
        parent =
            { ARDOUGNE }),

    /**
     * The willow trees north of Ardougne, south of Hemenster. Intended for mid-level woodcutters.
     * - 9 willow trees
     * - Normal trees
     * - Oak trees
     */
    NORTH_ARDOUGNE_WILLOWS(
        inside = Position(2627, 3401),
        area = SimpleBoxArea.of(2615, 3380, 2665, 3409),
        parent =
            { ARDOUGNE }),

    /**
     * The willow trees east of Ardougne, south of the Legends' Guild. Intended for mid-level woodcutters.
     * - 16 willow trees
     * - Normal trees
     * - Oak trees
     */
    EAST_ARDOUGNE_WILLOWS(
        inside = Position(2711, 3309),
        area = SimpleBoxArea.of(2690, 3285, 2725, 3325),
        parent =
            { ARDOUGNE }),

    /**
     * The willow trees south of Yanille. Intended for mid-level woodcutters.
     * - 5 willow trees
     * - Normal trees
     */
    SOUTH_YANILLE_WILLOWS(
        inside = Position(2609, 3060),
        area = SimpleBoxArea.of(2598, 3048, 2622, 3070),
        parent =
            { YANILLE }),

    /**
     * The willow trees west of Draynor Village. Intended for dexterous willow trainers.
     * - 4 willow trees
     * - Normal trees
     * - Oak trees
     */
    WEST_DRAYNOR_WILLOWS(
        inside = Position(3059, 3253),
        area = SimpleBoxArea.of(3050, 3244, 3072, 3262),
        parent =
            { DRAYNOR }),

    /**
     * The trees west of Varrock. Intended for low-level woodcutters.
     * - Normal trees
     * - Oak trees
     */
    WEST_VARROCK_TREES(
        inside = Position(3140, 3425),
        area = SimpleBoxArea.of(3110, 3395, 3170, 3455),
        parent =
            { VARROCK }),

    /**
     * The trees east of Edgeville. Intended for low-level woodcutters.
     * - Normal trees
     * - Oak trees
     */
    EAST_EDGEVILLE_TREES(
        inside = Position(3145, 3493),
        area = SimpleBoxArea.of(3100, 3460, 3170, 3518),
        parent =
            { EDGEVILLE }),

    /**
     * The iconic barbarian village, intended for low-level combat training, crafting training, and low -> high level
     * drop-fishing training.
     * - Fly fishing spots
     * - Barbarians
     * - Spinning wheel
     * - Pottery oven
     * - Potter's wheel
     */
    BARBARIAN_VILLAGE(
        inside = Position(3099, 3420),
        area = SimpleBoxArea.of(3070, 3402, 3109, 3451),
        parent =
            { EDGEVILLE }),

    /**
     * The Al-kharid bank area. Intended for average dexerity/intelligence bots that need to tan, smelt, cook, and do
     * low-level fishing training.
     * - Tanner NPC
     * - Furnace
     * - Kebab shop & range
     * - 2 small net/bait fishing spots
     */
    AL_KHARID_BANK(
        inside = Position(3270, 3167),
        area = SimpleBoxArea.of(3263, 3138, 3279, 3194),
        parent =
            { AL_KHARID }),

    /**
     * The goblin village area. Intended for beginner combat training.
     * - Plenty of low level goblins
     * - Infinitely burning fire
     */
    GOBLIN_VILLAGE(
        inside = Position(2955, 3502),
        area = SimpleBoxArea.of(2944, 3481, 2970, 3518),
        parent =
            { FALADOR }),

    /**
     * The main flax spinning area inside Thessalia's clothing store.
     */
    FLAX_SPINNING_MAIN(
        inside = Position(3207, 3415),
        area = SimpleBoxArea.of(3200, 3410, 3210, 3420),
        parent = { VARROCK }),

    /**
     * The main eastern lumbridge cow pen.
     */
    LUMBRIDGE_COW_PEN(
        inside = Position(3257, 3279),
        area = SimpleBoxArea.of(3240, 3253, 3265, 3298),
        parent = { LUMBRIDGE }),

    /**
     * The gated cow field north of the Lumbridge windmill.
     */
    NORTH_LUMBRIDGE_COW_FIELD(
        inside = Position(3175, 3320),
        entrance = { Position(3174, 3315) },
        area = SimpleBoxArea.of(3153, 3316, 3200, 3352),
        parent = { DRAYNOR }),

    /**
     * The gated cow field around the Crafting Guild.
     */
    CRAFTING_GUILD_COW_FIELD(
        inside = Position(2923, 3289),
        entrance = { Position(2923, 3293) },
        area = SimpleBoxArea.of(2911, 3264, 2938, 3292),
        parent = { FALADOR }),

    /**
     * The gated cow field north of East Ardougne.
     */
    NORTH_ARDOUGNE_COW_FIELD(
        inside = Position(2671, 3349),
        entrance = { Position(2676, 3349) },
        area = SimpleBoxArea.of(2642, 3340, 2675, 3359),
        parent = { ARDOUGNE }),

    /**
     * The open cow field north of Yanille.
     */
    NORTH_YANILLE_COW_FIELD(
        inside = Position(2590, 3117),
        area = SimpleBoxArea.of(2576, 3111, 2608, 3126),
        parent = { YANILLE }),

    GREEN_DRAGONS(
        inside = Position(3141, 3690),
        area = SimpleBoxArea.of(3120, 3690, 3155, 3721),
        parent = { WILDERNESS }),

    /**
     * The Wilderness Mage of Zamorak's roaming area, used before native Abyss entry.
     */
    ABYSS_ENTRY_MAGE(
        inside = Position(3107, 3557),
        area = SimpleBoxArea.of(3102, 3552, 3112, 3562),
        parent = { EDGEVILLE }),

    /**
     * Both Abyss rings. Runecrafting scripts use native mage, obstacle and rift interactions inside this footprint.
     */
    ABYSS(
        inside = Position(3039, 4856),
        area = SimpleBoxArea.ofRadius(3010, 4802, 61, 62),
        parent = { EDGEVILLE }),

    /**
     * The Air Altar interior.
     *
     * Bots enter by using an air talisman on the outside ruins west of Falador, then leave through the portal inside
     * the altar.
     */
    AIR_ALTAR(
        inside = Position(2842, 4832),
        entrance = { Position(2985, 3299) },
        area = SimpleBoxArea.of(2836, 4825, 2851, 4842),
        parent = { FALADOR }),

    /**
     * The Water Altar interior.
     *
     * Bots enter by using a water talisman on the outside ruins south-west of Lumbridge, then leave through the portal
     * inside the altar.
     */
    WATER_ALTAR(
        inside = Position(2724, 4833),
        entrance = { Position(3192, 3158) },
        area = SimpleBoxArea.of(2703, 4817, 2733, 4847),
        parent = { DRAYNOR }),

    /**
     * The Mind Altar interior.
     *
     * Bots enter by using a mind talisman on the outside ruins north of Falador, then leave through the portal inside
     * the altar.
     */
    MIND_ALTAR(
        inside = Position(2786, 4839),
        entrance = { Position(2979, 3511) },
        area = SimpleBoxArea.of(2760, 4818, 2802, 4855),
        parent = { EDGEVILLE }),

    /**
     * The Earth Altar interior.
     *
     * Bots enter by using an earth talisman on the outside ruins north-east of Varrock, then leave through the portal
     * inside the altar.
     */
    EARTH_ALTAR(
        inside = Position(2657, 4839),
        entrance = { Position(3305, 3472) },
        area = SimpleBoxArea.of(2626, 4813, 2685, 4862),
        parent = { VARROCK }),

    /**
     * The Fire Altar interior.
     *
     * Bots enter by using a fire talisman on the outside ruins north-east of Al Kharid, then leave through the portal
     * inside the altar.
     */
    FIRE_ALTAR(
        inside = Position(2584, 4836),
        entrance = { Position(3312, 3253) },
        area = SimpleBoxArea.of(2560, 4806, 2622, 4863),
        parent = { AL_KHARID }),

    /**
     * The Body Altar interior.
     *
     * Bots enter by using a body talisman on the outside ruins west of Barbarian Village, then leave through the portal
     * inside the altar.
     */
    BODY_ALTAR(
        inside = Position(2522, 4838),
        entrance = { Position(3052, 3443) },
        area = SimpleBoxArea.of(2506, 4822, 2538, 4856),
        parent = { EDGEVILLE }),

    /**
     * The Cosmic Altar interior.
     *
     * Bots enter by using a cosmic talisman on the outside ruins in Zanaris, then leave through the portal inside the
     * altar.
     */
    COSMIC_ALTAR(
        inside = Position(2141, 4831),
        entrance = { Position(2407, 4375) },
        area = SimpleBoxArea.of(2116, 4809, 2165, 4857),
        parent = { ZANARIS }),

    /**
     * The Chaos Altar interior.
     *
     * Bots enter by using a chaos talisman on the outside ruins in the Wilderness, then leave through the portal inside
     * the altar.
     */
    CHAOS_ALTAR(
        inside = Position(2270, 4840),
        entrance = { Position(3059, 3589) },
        area = SimpleBoxArea.of(2250, 4826, 2294, 4859),
        parent = { EDGEVILLE }),

    /**
     * The Nature Altar interior.
     *
     * Bots enter by using a nature talisman on the outside ruins north of Shilo Village, then leave through the portal
     * inside the altar.
     */
    NATURE_ALTAR(
        inside = Position(2399, 4839),
        entrance = { Position(2868, 3017) },
        area = SimpleBoxArea.of(2378, 4814, 2422, 4858),
        parent = { KARAMJA }),

    /**
     * The Law Altar interior.
     *
     * Bots enter by using a law talisman on the outside ruins on Entrana, then leave through the portal inside the
     * altar.
     */
    LAW_ALTAR(
        inside = Position(2463, 4830),
        entrance = { Position(2857, 3379) },
        area = SimpleBoxArea.of(2443, 4809, 2486, 4854),
        parent = { ENTRANA }),

    /**
     * The Death Altar interior.
     *
     * Bots enter by using a death talisman on the outside ruins in the Temple of Light, then leave through the portal
     * inside the altar.
     */
    DEATH_ALTAR(
        inside = Position(2204, 4834),
        entrance = { Position(1859, 4637) },
        area = SimpleBoxArea.of(2183, 4812, 2231, 4861),
        parent = { ARDOUGNE }),

    FALADOR_WEST_BANK(
        inside = Position(2945, 3370),
        area = SimpleBoxArea.of(2939, 3355, 2980, 3392),
        parent = { FALADOR }),

    /**
     * The Falador east bank and the open square north of its entrance. Intended for firemakers.
     */
    FALADOR_EAST_BANK(
        inside = Position(3013, 3360),
        area = SimpleBoxArea.of(3000, 3348, 3035, 3370),
        parent = { FALADOR }),

    /**
     * South-Ardougne Zamorak monks. Bounds cover the configured spawn positions and five-tile wandering.
     */
    SOUTH_ARDOUGNE_ZAMORAK_MONKS(
        inside = Position(2465, 3248),
        area = SimpleBoxArea.of(2449, 3233, 2482, 3253),
        parent = { ARDOUGNE }),

    /**
     * Taverley Dungeon's stronger Zamorak monks, using their existing spawn and wandering footprint.
     */
    TAVERLEY_DUNGEON_ZAMORAK_MONKS(
        inside = Position(2829, 9808),
        area = SimpleBoxArea.of(2820, 9800, 2839, 9819),
        parent = { TAVERLY }),

    /**
     * Varrock's Zamorak monk and its configured five-tile wandering area.
     */
    VARROCK_ZAMORAK_TEMPLE(
        inside = Position(3213, 3476),
        area = SimpleBoxArea.of(3208, 3471, 3218, 3481),
        parent = { VARROCK }),

    /**
     * Paterdomus Zamorak monks on both configured floors. The ground-floor anchor uses existing travel;
     * normal plane-aware target searches select only monks reachable on the bot's current floor.
     */
    PATERDOMUS_ZAMORAK_MONKS(
        inside = Position(3411, 3489),
        area = SimpleBoxArea.of(3405, 3479, 3421, 3498),
        parent = { VARROCK }),

    /**
     * The chaos temple north of Falador and north-west of Goblin Village. Primarily intended for low-level combat
     * training and telegrabbing wines money-making method.
     *
     * - Zamorak wine
     * - Plenty of lvl 17 monks of Zamorak
     */
    NORTH_FALADOR_CHAOS_TEMPLE(
        inside = Position(2934, 3515),
        area = SimpleBoxArea.of(2929, 3511, 2942, 3519),
        parent =
            { FALADOR }),

    /**
     * King Black Dragon lair, reached through the Wilderness ladder and dungeon lever.
     */
    KBD_LAIR(
        inside = Position(2271, 4680),
        entrance = { Position(3016, 3849) },
        area = SimpleBoxArea.of(2249, 4674, 2291, 4717),
        parent = { WILDERNESS }),

    /**
     * Queen chamber reached through both rope entrances. Existing ropes are reused without consuming stock.
     * Temporary transitions until the webwalker covers rope attachment and both lair descents.
     * Remove the overrides and outside anchor after https://github.com/luna-rs/luna/issues/707 has full route coverage.
     */
    KALPHITE_QUEEN(
        inside = Position(3485, 9498),
        entrance = { Position(3226, 3108) },
        area = SimpleBoxArea.of(3456, 9472, 3519, 9535),
        parent = { AL_KHARID }),

    /**
     * Level 12 Wilderness chaos temple.
     *
     * Used for:
     * - Telegrabbing wines money-making method.
     *
     * Remaining work:
     * - Determine whether this subzone needs inside, outside, or both.
     */
    LVL_12_WILDERNESS_CHAOS_TEMPLE(
        inside = Position(3239, 3619),
        area = SimpleBoxArea.ofRadius(3221, 3590, 38, 44),
        parent = { EDGEVILLE }),

    /**
     * Level 40 Wilderness chaos temple.
     *
     * Used for:
     * - Telegrabbing wines money-making method.
     *
     * Remaining work:
     * - Determine whether this subzone needs inside, outside, or both.
     */
    LVL_40_WILDERNESS_CHAOS_TEMPLE(
        inside = Position(2956, 3820),
        area = SimpleBoxArea.ofRadius(2943, 3813, 16, 14),
        parent = { EDGEVILLE });

    companion object {

        /**
         * Maps each loaded map region to the subzones that touch it.
         *
         * This is used as a cheap pre-filter before checking exact tile containment. A bot only scans local subzones
         * for its current region instead of checking every subzone every time it moves.
         */
        val LOCAL: ImmutableSetMultimap<Int, SubZone> = run {
            val map = HashMultimap.create<Int, SubZone>()

            for (zone in entries) {
                for (region in zone.area.touchedRegions) {
                    map.put(region, zone)
                }
            }

            ImmutableSetMultimap.copyOf(map)
        }

        /**
         * Updates the cached local subzone list for a bot after its region changes.
         *
         * This should be called from movement/region-change handling before [updateSubZone]. The cached list keeps
         * subzone detection cheap by only checking subzones that touch the bot's current region.
         *
         * @param bot The bot whose local subzone cache is being updated.
         * @param newRegion The bot's new map region id.
         */
        fun updateLocalSubZones(bot: Bot, newRegion: Int) {
            bot.localSubZones = LOCAL[newRegion]
        }

        /**
         * Updates the bot's current subzones from its cached local subzones.
         *
         * [Bot.subZones] contains every cached local subzone whose [SubZone.area] contains the bot.
         * The set is empty when no local subzone contains the bot.
         *
         * @param bot The bot whose current subzone is being updated.
         */
        fun updateSubZone(bot: Bot) {
            // TODO Is there a way to do this where it doesn't have to be recalculated every step?
            bot.subZones.clear()
            if (bot.localSubZones.isNotEmpty()) {
                for (zone in bot.localSubZones) {
                    if (bot.position.z == zone.area.plane && bot in zone.area) {
                        bot.subZones.add(zone)
                    }
                }
            }
        }
    }

    /**
     * Checks whether a [Bot] is close to this subzone's local or external anchor.
     *
     * @param bot The bot to check.
     * @param distance The maximum allowed tile distance.
     * @return `true` if [bot] is within [distance] of [inside] or [entrance].
     */
    fun isWithinDistance(bot: Bot, distance: Int): Boolean {
        if (this in bot.subZones || inside.isWithinDistance(bot, distance)) {
            return true
        }
        val outsidePosition = entrance(bot)
        return outsidePosition != null && outsidePosition.isWithinDistance(bot, distance)
    }

    /**
     * Finds what keeps a [Bot] out of this subzone, for zones behind a door that not every bot can open.
     *
     * @param bot The bot to check.
     * @return Why [bot] can't get in, or `null` if nothing stops it.
     */
    fun entryBlocker(bot: Bot): String? = when (this) {
        CHAOS_DRUID_TOWER, CHAOS_DRUID_TOWER_DUNGEON ->
            if (bot.thieving.level < ChaosDruidTowerDoorAction.REQUIRED_LEVEL &&
                CHAOS_DRUID_TOWER !in bot.subZones && CHAOS_DRUID_TOWER_DUNGEON !in bot.subZones)
                "the tower door needs level ${ChaosDruidTowerDoorAction.REQUIRED_LEVEL} Thieving" else null
        EDGEVILLE_DUNGEON_MINE, EDGEVILLE_DUNGEON_HILL_GIANTS ->
            // A bot without a key buys one for a coin.
            if (this !in bot.subZones && !bot.itemTracker.contains(BrassKeyDoor.KEY) &&
                !bot.inventory.contains(Currency.COINS.id) && !bot.bank.contains(Currency.COINS.id))
                "the house door needs a brass key" else null
        else -> null
    }

    /**
     * The item that a bot must carry to get into this subzone, which it keeps when banking.
     */
    val entryItem: Int?
        get() = when (this) {
            EDGEVILLE_DUNGEON_MINE, EDGEVILLE_DUNGEON_HILL_GIANTS -> BrassKeyDoor.KEY
            else -> null
        }
}
