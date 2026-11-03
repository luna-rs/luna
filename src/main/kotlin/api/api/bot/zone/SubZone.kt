package api.bot.zone

import api.bot.zone.SubZone.Companion.updateSubZone
import api.bot.zone.Zone.*
import api.predef.*
import com.google.common.collect.HashMultimap
import com.google.common.collect.ImmutableSetMultimap
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.item.teleport.SpadeDigSpot
import game.obj.doors.BrassKeyDoor
import game.obj.doors.TaverleyJailDoor
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
 * - [bankZone], the zone whose banks bots use, which is [parent] unless a nearer bank lies in another zone.
 *
 * @author lare96
 */
enum class SubZone(
    val inside: Position,
    val entrance: Bot.() -> Position? = { null },
    val area: SimpleBoxArea,
    val parent: Bot.() -> Zone,
    val bankZone: Bot.() -> Zone = parent) {
    /**
     * Mainland bush patches from the revision 377 maps. Rimmington has its own travel parent and banks in Falador.
     */
    CHAMPIONS_GUILD_BUSH_PATCH(
        inside = Position(3180, 3357), area = SimpleBoxArea.of(3178, 3354, 3185, 3361),
        parent = { VARROCK }),
    RIMMINGTON_BUSH_PATCH(
        inside = Position(2939, 3221), area = SimpleBoxArea.of(2937, 3218, 2944, 3225),
        parent = { RIMMINGTON }, bankZone = { FALADOR }),
    ARDOUGNE_BUSH_PATCH(
        inside = Position(2616, 3225), area = SimpleBoxArea.of(2614, 3222, 2621, 3229),
        parent = { ARDOUGNE }),

    /**
     * Etceteria bush patch beside the eastern shore. The ground-floor arrival stands on the walkable approach
     * beside its native bush plot rather than on the blocked planting tile.
     */
    ETCETERIA_BUSH_PATCH(
        inside = Position(2590, 3863), area = SimpleBoxArea.ofRadius(2574, 3855, 20, 17),
        parent = { MISCELLANIA_AND_ETCETERIA }),

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

    /**
     * Supplied fruit-tree plot outside Tree Gnome Village, using its own parent travel hub.
     */
    GNOME_VILLAGE_FRUIT_PATCH(
        inside = Position(2490, 3182), area = SimpleBoxArea.ofRadius(2482, 3175, 14, 12),
        parent = { TREE_GNOME_VILLAGE }),
    BRIMHAVEN_FRUIT_PATCH(
        inside = Position(2763, 3212), area = SimpleBoxArea.of(2761, 3209, 2769, 3217),
        parent = { BRIMHAVEN }, bankZone = { ARDOUGNE }),
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
     * Port Phasmatys allotments, flower and herb plots and compost bin share this farm footprint.
     */
    PORT_PHASMATYS_FARM(
        inside = Position(3636, 3527), area = SimpleBoxArea.ofRadius(3584, 3516, 53, 21),
        parent = { PORT_PHASMATYS }),

    /**
     * Ground-floor Slayer Tower assignments, using Port Phasmatys as the supplied travel and banking parent.
     */
    SLAYER_TOWER_GROUND(
        inside = Position(3429, 3538), area = SimpleBoxArea.ofRadius(3401, 3526, 53, 56),
        parent = { PORT_PHASMATYS }),

    /**
     * Reuses the supplied Tower footprint on its first upper floor. Arrival is an existing Infernal Mage spawn;
     * native target IDs distinguish the assignment from other monsters sharing this floor.
     */
    SLAYER_TOWER_FIRST_FLOOR(
        inside = Position(3434, 3563, 1), area = SimpleBoxArea.ofRadius(3401, 3526, 53, 56, 1),
        parent = { PORT_PHASMATYS }),

    /**
     * Reuses the supplied Tower footprint on its second upper floor, arriving at a configured Gargoyle spawn.
     * Assignment filtering keeps the other monsters on this floor out of deliberate target selection.
     */
    SLAYER_TOWER_SECOND_FLOOR(
        inside = Position(3435, 3548, 2), area = SimpleBoxArea.ofRadius(3401, 3526, 53, 56, 2),
        parent = { PORT_PHASMATYS }),

    /**
     * Supplied shared footprint for the Fremennik Slayer Dungeon. Exact assignment IDs select each room's monsters;
     * native combat and the shared Slayer preparation policy enforce their equipment and finishing requirements.
     */
    FREMENNIK_SLAYER_DUNGEON(
        inside = Position(2803, 9999), entrance = { Position(2631, 10016) },
        area = SimpleBoxArea.ofRadius(2685, 9949, 131, 99), parent = { RELLEKKA }),

    /**
     * Matches the native Giant Mole darkness bounds and spade landing. The full lair includes every native surfacing
     * spot, so existing targeting and travel recovery can find the boss again after it burrows.
     */
    GIANT_MOLE_LAIR(
        inside = SpadeDigSpot.FALADOR_MOLE_HILLS.landing,
        area = SimpleBoxArea.of(1732, 5133, 1787, 5244), parent = { FALADOR }),

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
        parent = { Zone.SHILO_VILLAGE }),

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
     * Fishing on the Ardougne river north of town. Arrival is the customer tile in front of the north bank booth.
     */
    ARDOUGNE_RIVER_FISHING(
        inside = Position(2618, 3332),
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
     * enclosing footprints retain nearby banking/resource memberships. Al Kharid uses a local bank-market arrival
     * because its town travel anchor is outside the marketplace.
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
        inside = Position(3272, 3167),

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
        area = SimpleBoxArea.ofRadius(2467, 3413, 25, 29, -1),
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
        area = SimpleBoxArea.of(2530, 3540, 2555, 3558, -1),
        parent = { SEERS_VILLAGE }),

    /**
     * Wilderness course north of the entrance ridge. Failure pits are recovered by the script.
     */
    WILDERNESS_AGILITY(
        inside = Position(2998, 3933),
        entrance = { Position(2998, 3916) },
        area = SimpleBoxArea.of(2990, 3933, 3008, 3968),
        parent = { WILDERNESS }),

    /**
     * Ape Atoll's six-obstacle ninja-monkey course, including its river failure banks and the monkey bars on
     * plane two. The native handlers require a wielded ninja greegree before any obstacle can succeed.
     */
    APE_ATOLL_AGILITY(
        inside = Position(2755, 2742),
        area = SimpleBoxArea.of(2743, 2728, 2772, 2749, -1),
        parent = { APE_ATOLL }),

    /**
     * The western Werewolf course below Canifis: five stepping stones, three hurdle rows, three pipe lanes,
     * a skull slope and the deathslide. The footprint includes every fall landing and the stick trainer.
     */
    WEREWOLF_AGILITY(
        inside = Position(3538, 9873),
        entrance = { Position(3543, 3462) },
        area = SimpleBoxArea.of(3524, 9864, 3551, 9914),
        parent = { CANIFIS }),

    /**
     * The Agility Pyramid yard, Simon Templeton and the first three raised obstacle levels. The western rock
     * slide supplies native access from the desert; ordinary food resupply retains Al Kharid banking.
     */
    AGILITY_PYRAMID(
        inside = Position(3353, 2831),
        entrance = { Position(3343, 2844) },
        area = SimpleBoxArea.of(3342, 2827, 3379, 2856, -1),
        parent = { KHARIDIAN_DESERT }, bankZone = { AL_KHARID }),

    /**
     * The native map copy containing Pyramid levels four and five on planes two and three. Its separate
     * rectangle avoids treating the intervening world as course space; the level-three stairs lead here.
     */
    AGILITY_PYRAMID_UPPER(
        inside = Position(3040, 4696, 2),
        area = SimpleBoxArea.of(3040, 4693, 3049, 4702, -1),
        parent = { KHARIDIAN_DESERT }, bankZone = { AL_KHARID }),

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
        parent = { KARAMJA }, bankZone = { DRAYNOR }),

    /**
     * Supplied Brimhaven Dungeon Fire Giant camp, covering the five configured ground-floor giants.
     * Combat and assignment sessions share ordinary webwalking and the existing travel fallback.
     */
    BRIMHAVEN_DUNGEON_FIRE_GIANTS(
        inside = Position(2651, 9502),
        area = SimpleBoxArea.ofRadius(2632, 9473, 56, 35),
        parent = { BRIMHAVEN }, bankZone = { ARDOUGNE }),

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
        inside = Position(3220, 3363),
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

    /**
     * Rimmington's open mine: two clay, five copper, two tin, six iron and two gold rocks.
     */
    RIMMINGTON_MINE(
        inside = Position(2977, 3244),
        area = SimpleBoxArea.of(2965, 3230, 2990, 3251),
        parent = { RIMMINGTON }, bankZone = { FALADOR }),

    /**
     * The south-west bank of the Port Khazard mine, beside the Fight Arena: four clay, two copper,
     * seven tin, nine iron, one coal and two mithril rocks. The opposite riverbank is a separate mine.
     */
    PORT_KHAZARD_SOUTH_WEST_MINE(
        inside = Position(2631, 3141),
        area = SimpleBoxArea.of(2622, 3128, 2639, 3154),
        parent = { YANILLE }),

    /**
     * The north-east bank of the Port Khazard mine: two copper, two tin and two mithril rocks.
     */
    PORT_KHAZARD_NORTH_EAST_MINE(
        inside = Position(2652, 3169),
        area = SimpleBoxArea.of(2639, 3148, 2659, 3176),
        parent = { YANILLE }),

    /**
     * Rocks outside West Ardougne beside the Zamorak monks: three copper and one tin rock.
     */
    WEST_ARDOUGNE_MINE(
        inside = Position(2470, 3254),
        area = SimpleBoxArea.of(2465, 3230, 2483, 3259),
        parent = { ARDOUGNE }),

    /**
     * Barbarian Village's central mining patch: five tin and four coal rocks, banking in Edgeville.
     */
    BARBARIAN_VILLAGE_MINE(
        inside = Position(3081, 3421),
        area = SimpleBoxArea.of(3076, 3415, 3086, 3425),
        parent = { EDGEVILLE }),

    /**
     * The mine south of Piscatoris Fishing Colony: two clay, two copper, two tin and three iron rocks.
     * Uses the colony bank through the native shore tunnel and gate crossings.
     */
    PISCATORIS_MINE(
        inside = Position(2336, 3642),
        area = SimpleBoxArea.of(2331, 3636, 2342, 3649),
        parent = { PISCATORIS_FISHING_COLONY }),

    /**
     * The southern Keldagrim entrance cavern: two copper and five coal rocks, reached through the
     * existing Rellekka tunnel and cave entrances without crossing the underground river.
     */
    KELDAGRIM_ENTRANCE_SOUTH_MINE(
        inside = Position(2867, 10119),
        entrance = { Position(2730, 3713) },
        area = SimpleBoxArea.of(2859, 10112, 2876, 10127),
        parent = { RELLEKKA }),

    /**
     * The wizards' tower area. Intended for low-level combat training.
     * - Low level wizards
     */
    WIZARDS_TOWER(
        inside = Position(3109, 3164),

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
        inside = Position(2640, 3424),
        area = SimpleBoxArea.of(2638, 3421, 2641, 3427),
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
        inside = Position(3110, 9935),
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
     * Supplied Blue Dragon camp with the confirmed ground-floor arrival inside its native spawn footprint.
     * Existing travel fallback and shared dragon equipment preparation handle the trip.
     */
    TAVERLY_DUNGEON_BLUE_DRAGONS(
        inside = Position(2908, 9807),
        area = SimpleBoxArea.ofRadius(2888, 9792, 39, 24),
        parent = { TAVERLY }),

    /**
     * The rock crabs north of Rellekka. Intended for intelligent mid-level combat training.
     */
    ROCK_CRABS(
        inside = Position(2670, 3715),
        area = SimpleBoxArea.of(2648, 3711, 2722, 3742),
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
        inside = Position(3038, 3305),

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
        parent = { KARAMJA }, bankZone = { DRAYNOR }),

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
        inside = Position(2839, 3434),

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
     * Paid Tai Bwo Wannai enclosure containing nine teak and four mahogany placements. The western gate charges
     * 100 trading sticks per entry; Gabooty's native gem trade supplies currency and the gate permits free exits.
     */
    TAI_BWO_WANNAI_HARDWOOD_GROVE(
        inside = Position(2818, 3083),
        area = SimpleBoxArea.of(2818, 3077, 2830, 3091),
        parent = { KARAMJA }, bankZone = { Zone.SHILO_VILLAGE }),

    /**
     * Southern Karamja pond with three small-net karambwanji spots. The northern shore arrival supplies real bait
     * for northeast Karamja's vessel fishing; shrimp share the native catch table.
     */
    KARAMJA_KARAMBWANJI_FISHING(
        inside = Position(2791, 3020),
        area = SimpleBoxArea.of(2785, 3005, 2812, 3027),
        parent = { KARAMJA }, bankZone = { Zone.SHILO_VILLAGE }),

    /**
     * Western Mort Myre pools south of Canifis, containing six bait-fishing spots for slimy eels. Arrival stays
     * on the walkable bank beside the western pool rather than on a water-bound NPC anchor.
     */
    MORT_MYRE_SLIMY_EEL_FISHING(
        inside = Position(3424, 3410),
        area = SimpleBoxArea.of(3418, 3401, 3446, 3422),
        parent = { CANIFIS }, bankZone = { PORT_PHASMATYS }),

    /**
     * Western Lumbridge Swamp Cave pool with one bait-fishing spot for slimy and cave eels. Cave trips require
     * an owned enclosed light and the entrance's personal rope state or a carried rope.
     */
    LUMBRIDGE_WEST_CAVE_EEL_FISHING(
        inside = Position(3155, 9544),
        area = SimpleBoxArea.of(3147, 9539, 3161, 9552),
        parent = { LUMBRIDGE }),

    /**
     * Eastern Lumbridge Swamp Cave pool, containing the second native bait-fishing spot for cave and slimy eels.
     * The west-bank arrival avoids the pool; the same enclosed-light admission applies as at the western pool.
     */
    LUMBRIDGE_EAST_CAVE_EEL_FISHING(
        inside = Position(3244, 9570),
        area = SimpleBoxArea.of(3238, 9564, 3253, 9577),
        parent = { LUMBRIDGE }),

    /**
     * Taverley Dungeon lava shore with three lava-eel bait spots. Fishing requires an owned oily rod and bait;
     * the arrival is on the northern shore, outside the lava tiles occupied by the spots.
     */
    TAVERLY_LAVA_EEL_FISHING(
        inside = Position(2890, 9767),
        area = SimpleBoxArea.of(2883, 9760, 2897, 9774),
        parent = { TAVERLY }),

    /**
     * The karambwan spots on northeast Karamja, north of the shipyard.
     */
    KARAMJA_KARAMBWAN_FISHING(
        inside = Position(2905, 3117),
        area = SimpleBoxArea.of(2895, 3112, 2916, 3121),
        parent = { KARAMJA }, bankZone = { DRAYNOR }),

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
        // Shilo Village's bank is behind its gate.
        // TODO Once Shilo Village is added, bots that have finished it bank in the village (SHILO_VILLAGE).
        parent = { KARAMJA }, bankZone = { DRAYNOR }),

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
     * The public room beside Varrock Palace's imprisoned Zamorak monk. The cage itself has no walking entrance.
     */
    VARROCK_ZAMORAK_TEMPLE(
        inside = Position(3215, 3476),
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
        parent = { KHARIDIAN_DESERT }, bankZone = { AL_KHARID }),

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
        parent = { EDGEVILLE }),

    /**
     * Deep Wilderness runite rocks, used by profit miners with Varrock as their travel and banking parent.
     */
    DEEP_WILDERNESS_RUNITE_ROCKS(
        inside = Position(3062, 3884),
        area = SimpleBoxArea.ofRadius(3052, 3878, 15, 12),
        parent = { VARROCK }),

    /**
     * The Coal Trucks seam west of Seers Village has eighteen native coal rocks. Ordinary miners use the
     * existing town bank without a cart workflow.
     */
    COAL_TRUCKS_MINE(
        inside = Position(2582, 3480),
        area = SimpleBoxArea.of(2569, 3469, 2595, 3490),
        parent = { SEERS_VILLAGE }),

    /**
     * The small Karamja seam north of Shilo Village has two mithril and two adamant rocks. Ordinary mining uses
     * the existing Draynor banking fallback.
     */
    KARAMJA_NORTH_SHILO_MINE(
        inside = Position(2848, 3035),
        area = SimpleBoxArea.of(2841, 3025, 2857, 3041),
        parent = { KARAMJA }, bankZone = { DRAYNOR }),

    /**
     * The limestone quarry east of Varrock has eight native limestone rocks. The native mining action owns its
     * shrinking rock stages.
     */
    VARROCK_LIMESTONE_QUARRY(
        inside = Position(3373, 3500),
        area = SimpleBoxArea.of(3365, 3493, 3380, 3507),
        parent = { VARROCK }),

    /**
     * The desert quarry has thirty-two sandstone and twenty-five exposed granite placements. An enclosed
     * granite tile remains subject to ordinary failed-target recovery.
     */
    DESERT_QUARRY(
        inside = Position(3168, 2911),
        area = SimpleBoxArea.of(3158, 2896, 3185, 2927),
        parent = { KHARIDIAN_DESERT }, bankZone = { AL_KHARID }),

    /**
     * Woodland northwest of Lumbridge Castle contains twenty-two normal trees and three oaks. The footprint
     * follows the open woodland below the northern fenced field.
     */
    LUMBRIDGE_WOODLAND(
        inside = Position(3200, 3248),
        area = SimpleBoxArea.of(3184, 3237, 3217, 3259),
        parent = { LUMBRIDGE }),

    /**
     * Twelve willows follow the coast south of Rimmington. Woodcutting trips return to Falador for banking.
     */
    RIMMINGTON_COAST_WILLOWS(
        inside = Position(2974, 3194),
        area = SimpleBoxArea.of(2956, 3179, 2995, 3205),
        parent = { RIMMINGTON }, bankZone = { FALADOR }),

    /**
     * Fourteen willows line the coast southwest of Port Sarim. The coastal footprint keeps their accessible
     * shoreline stands together.
     */
    PORT_SARIM_COAST_WILLOWS(
        inside = Position(3013, 3170),
        area = SimpleBoxArea.of(2991, 3155, 3033, 3181),
        parent = { PORT_SARIM }, bankZone = { FALADOR }),

    /**
     * Two willows stand beside the western end of the Gnome Stronghold river bridge. This bank has its own
     * local arrival.
     */
    GNOME_STRONGHOLD_WEST_WILLOWS(
        inside = Position(2384, 3431),
        area = SimpleBoxArea.of(2379, 3424, 2389, 3439),
        parent = { GNOME_STRONGHOLD }),

    /**
     * A single willow stands on the western river bank northwest of the Gnome Stronghold bridge.
     */
    GNOME_STRONGHOLD_NORTH_WEST_WILLOW(
        inside = Position(2374, 3445),
        area = SimpleBoxArea.of(2370, 3440, 2380, 3451),
        parent = { GNOME_STRONGHOLD }),

    /**
     * Four willows follow the eastern bank of the Stronghold river between its southern bend and northern
     * woodland.
     */
    GNOME_STRONGHOLD_EAST_WILLOWS(
        inside = Position(2393, 3429),
        area = SimpleBoxArea.of(2389, 3413, 2402, 3445),
        parent = { GNOME_STRONGHOLD }),

    /**
     * A single willow stands beside the eastern shore north of the Gnome Stronghold river bridge.
     */
    GNOME_STRONGHOLD_NORTH_EAST_WILLOW(
        inside = Position(2384, 3449),
        area = SimpleBoxArea.of(2378, 3444, 2388, 3454),
        parent = { GNOME_STRONGHOLD }),

    /**
     * Eight willows line the lake in northern Gnome Stronghold. The nearby woodland provides the normal
     * chopping stands.
     */
    GNOME_STRONGHOLD_NORTH_WILLOWS(
        inside = Position(2478, 3538),
        area = SimpleBoxArea.of(2464, 3527, 2492, 3549),
        parent = { GNOME_STRONGHOLD }),

    /**
     * A maple grows south of Seers Village, west of the Party Room. The stand uses ordinary Seers travel and
     * banking.
     */
    SEERS_SOUTH_WEST_MAPLE(
        inside = Position(2721, 3464),
        area = SimpleBoxArea.of(2716, 3460, 2727, 3471),
        parent = { SEERS_VILLAGE }),

    /**
     * Two maples grow southeast of Seers Village. Both use the local town banking parent.
     */
    SEERS_SOUTH_EAST_MAPLES(
        inside = Position(2751, 3466),
        area = SimpleBoxArea.of(2744, 3460, 2755, 3473),
        parent = { SEERS_VILLAGE }),

    /**
     * A solitary maple grows northeast of the Barbarian Outpost beside the water. Seers Village remains its
     * travel and banking parent.
     */
    BARBARIAN_OUTPOST_MAPLE(
        inside = Position(2592, 3595),
        area = SimpleBoxArea.of(2587, 3588, 2598, 3599),
        parent = { SEERS_VILLAGE }),

    /**
     * Two native yews grow in the ruined building south of Edgeville bank. Both have chopping stands in the
     * same local courtyard.
     */
    EDGEVILLE_YEWS(
        inside = Position(3086, 3471),
        area = SimpleBoxArea.of(3080, 3463, 3092, 3486),
        parent = { EDGEVILLE }),

    /**
     * Four yews grow along the wooded hillside west of Rimmington. Falador supplies the existing travel and
     * banking parent.
     */
    RIMMINGTON_YEWS(
        inside = Position(2938, 3229),
        area = SimpleBoxArea.of(2928, 3220, 2946, 3239),
        parent = { RIMMINGTON }, bankZone = { FALADOR }),

    /**
     * Three yews grow around the Stronghold bank woodland. Their chopping stands share the same local ground
     * component.
     */
    GNOME_STRONGHOLD_BANK_YEWS(
        inside = Position(2437, 3435),
        area = SimpleBoxArea.of(2427, 3420, 2444, 3446),
        parent = { GNOME_STRONGHOLD }),

    /**
     * Four yews grow in southern Gnome Stronghold, inside the southern settlement approach.
     */
    GNOME_STRONGHOLD_SOUTH_YEWS(
        inside = Position(2488, 3396),
        area = SimpleBoxArea.of(2473, 3387, 2499, 3406),
        parent = { GNOME_STRONGHOLD }),

    /**
     * A solitary yew grows near the eastern side of Gnome Stronghold, northeast of its main woodland.
     */
    GNOME_STRONGHOLD_EAST_YEW(
        inside = Position(2496, 3489),
        area = SimpleBoxArea.of(2491, 3485, 2502, 3496),
        parent = { GNOME_STRONGHOLD }),

    /**
     * A magic tree grows in western Gnome Stronghold. Ordinary Woodcutting uses its adjacent ground stand.
     */
    GNOME_STRONGHOLD_WEST_MAGIC(
        inside = Position(2371, 3425),
        area = SimpleBoxArea.of(2366, 3421, 2377, 3432),
        parent = { GNOME_STRONGHOLD }),

    /**
     * A magic tree grows in the woodland south of the Stronghold bank. The same settlement supplies travel and
     * banking.
     */
    GNOME_STRONGHOLD_BANK_MAGIC(
        inside = Position(2432, 3409),
        area = SimpleBoxArea.of(2427, 3405, 2438, 3416),
        parent = { GNOME_STRONGHOLD }),

    /**
     * A magic tree grows in southeastern Gnome Stronghold. It has a separate local chopping stand from the
     * western trees.
     */
    GNOME_STRONGHOLD_EAST_MAGIC(
        inside = Position(2490, 3413),
        area = SimpleBoxArea.of(2485, 3409, 2496, 3420),
        parent = { GNOME_STRONGHOLD }),

    /**
     * One magic tree grows in the western courtyard outside the Mage Training Arena. The native courtyard walls
     * remain part of ordinary approach routing.
     */
    MTA_WEST_MAGIC_TREE(
        inside = Position(3355, 3311),
        area = SimpleBoxArea.of(3351, 3306, 3360, 3316),
        parent = { AL_KHARID }),

    /**
     * One magic tree grows in the eastern courtyard outside the Mage Training Arena. It has a separate arrival
     * from the western courtyard.
     */
    MTA_EAST_MAGIC_TREE(
        inside = Position(3367, 3311),
        area = SimpleBoxArea.of(3363, 3306, 3373, 3316),
        parent = { AL_KHARID }),

    /**
     * Achey woodland in the Feldip lowlands south of Yanille contains twenty-four native achey trees for ogre-
     * log supplies.
     */
    FELDIP_ACHEY_TREES(
        inside = Position(2613, 2968),
        area = SimpleBoxArea.of(2584, 2944, 2635, 2988),
        parent = { YANILLE }),

    /**
     * Woodland west of Port Phasmatys contains seventy-seven hollow trees. The native chopping action supplies
     * their bark.
     */
    PORT_PHASMATYS_HOLLOW_TREES(
        inside = Position(3583, 3486),
        area = SimpleBoxArea.of(3522, 3456, 3647, 3517),
        parent = { PORT_PHASMATYS }),

    /**
     * Rimmington's coastline has eight native net-and-bait fishing destinations. Ordinary tools use adjacent
     * shore stands.
     */
    RIMMINGTON_COAST_FISHING(
        inside = Position(2991, 3169),
        area = SimpleBoxArea.of(2981, 3150, 3002, 3183),
        parent = { RIMMINGTON }, bankZone = { FALADOR }),

    /**
     * Entrana's southern sea-fishing area has ten net-and-bait destinations. The monks' normal inventory and
     * equipment restrictions apply to every trip.
     */
    ENTRANA_SEA_FISHING(
        inside = Position(2878, 3335),
        area = SimpleBoxArea.of(2870, 3326, 2884, 3347),
        parent = { ENTRANA }, bankZone = { DRAYNOR }),

    /**
     * Two lure-and-bait fishing destinations use the southern bank of Entrana's river. This bank has its own
     * walkable arrival.
     */
    ENTRANA_RIVER_SOUTH_FISHING(
        inside = Position(2845, 3355),
        area = SimpleBoxArea.of(2835, 3351, 2851, 3357),
        parent = { ENTRANA }, bankZone = { DRAYNOR }),

    /**
     * Five lure-and-bait fishing destinations use the northern bank of Entrana's river. This bank has its own
     * walkable arrival.
     */
    ENTRANA_RIVER_NORTH_FISHING(
        inside = Position(2847, 3362),
        area = SimpleBoxArea.of(2837, 3358, 2854, 3366),
        parent = { ENTRANA }, bankZone = { DRAYNOR }),

    /**
     * Rellekka's southern piers have seven net-and-bait and ten cage-and-harpoon destinations. Both piers share
     * an accessible shore approach.
     */
    RELLEKKA_SOUTH_FISHING(
        inside = Position(2640, 3694),
        area = SimpleBoxArea.of(2628, 3682, 2647, 3705),
        parent = { RELLEKKA }),

    /**
     * Rellekka's northern pier has eight big-net and shark-harpoon destinations. It uses a separate local
     * arrival from the southern piers.
     */
    RELLEKKA_NORTH_FISHING(
        inside = Position(2647, 3710),
        area = SimpleBoxArea.of(2639, 3703, 2654, 3717),
        parent = { RELLEKKA }),

    /**
     * Seers' Village smithy, containing three anvils for smithing owned bars.
     */
    SEERS_VILLAGE_ANVILS(
        inside = Position(2713, 3493),
        area = SimpleBoxArea.of(2708, 3488, 2718, 3499),
        parent = { SEERS_VILLAGE }),

    /**
     * Yanille's smithy, containing three anvils for smithing owned bars.
     */
    YANILLE_ANVILS(
        inside = Position(2613, 3079),
        area = SimpleBoxArea.of(2609, 3074, 2620, 3085),
        parent = { YANILLE }),

    /**
     * Rellekka's western workshop: three anvils, two furnace objects, one pottery wheel, one pottery oven and
     * one spinning wheel.
     */
    RELLEKKA_WORKSHOP(
        inside = Position(2617, 3662),
        area = SimpleBoxArea.of(2612, 3655, 2624, 3670),
        parent = { RELLEKKA }),

    /**
     * Lumbridge's furnace north of the castle, containing one furnace for smelting and furnace crafting.
     */
    LUMBRIDGE_FURNACE(
        inside = Position(3226, 3255),
        area = SimpleBoxArea.of(3222, 3252, 3230, 3260),
        parent = { LUMBRIDGE }),

    /**
     * Ardougne's western furnace, containing one furnace for smelting and furnace crafting.
     */
    ARDOUGNE_FURNACE(
        inside = Position(2600, 3310),
        area = SimpleBoxArea.of(2597, 3306, 2605, 3314),
        parent = { ARDOUGNE }),

    /**
     * Furnace north of Port Phasmatys bank, containing one furnace for smelting and furnace crafting.
     */
    PORT_PHASMATYS_FURNACE(
        inside = Position(3686, 3478),
        area = SimpleBoxArea.of(3683, 3474, 3691, 3482),
        parent = { PORT_PHASMATYS }),

    /**
     * Shilo Village's furnace beside the local bank, containing one furnace for smelting and furnace crafting.
     */
    SHILO_VILLAGE_FURNACE(
        inside = Position(2855, 2966),
        area = SimpleBoxArea.of(2852, 2962, 2860, 2970),
        parent = { Zone.SHILO_VILLAGE }),

    /**
     * Lumbridge Castle kitchen, containing its one special cooking range.
     */
    LUMBRIDGE_CASTLE_RANGE(
        inside = Position(3211, 3215),
        area = SimpleBoxArea.of(3208, 3211, 3216, 3219),
        parent = { LUMBRIDGE }),

    /**
     * House southeast of Lumbridge Castle, containing one cooking range.
     */
    LUMBRIDGE_SOUTH_RANGE(
        inside = Position(3230, 3195),
        area = SimpleBoxArea.of(3226, 3192, 3234, 3200),
        parent = { LUMBRIDGE }),

    /**
     * Catherby kitchen beside the bank, containing one cooking range.
     */
    CATHERBY_SOUTH_RANGE(
        inside = Position(2817, 3443),
        area = SimpleBoxArea.of(2813, 3440, 2821, 3448),
        parent = { CATHERBY }),

    /**
     * Northern Catherby house, containing one cooking range.
     */
    CATHERBY_NORTH_RANGE(
        inside = Position(2819, 3454),
        area = SimpleBoxArea.of(2814, 3451, 2822, 3459),
        parent = { CATHERBY }),

    /**
     * Southern Seers' Village house, containing one cooking range.
     */
    SEERS_VILLAGE_RANGE(
        inside = Position(2715, 3477),
        area = SimpleBoxArea.of(2711, 3472, 2719, 3480),
        parent = { SEERS_VILLAGE }),

    /**
     * House south of Ardougne's north bank, containing one cooking range.
     */
    ARDOUGNE_NORTH_RANGE(
        inside = Position(2616, 3317),
        area = SimpleBoxArea.of(2613, 3313, 2621, 3321),
        parent = { ARDOUGNE }),

    /**
     * House north of Ardougne's south bank, containing one cooking range.
     */
    ARDOUGNE_SOUTH_RANGE(
        inside = Position(2647, 3298),
        area = SimpleBoxArea.of(2644, 3293, 2652, 3301),
        parent = { ARDOUGNE }),

    /**
     * Western Yanille cooking room, containing one cooking range.
     */
    YANILLE_WEST_RANGE(
        inside = Position(2566, 3103),
        area = SimpleBoxArea.of(2562, 3100, 2570, 3108),
        parent = { YANILLE }),

    /**
     * Eastern Falador cooking room, containing one cooking range.
     */
    FALADOR_EAST_RANGE(
        inside = Position(3037, 3342),
        area = SimpleBoxArea.of(3032, 3338, 3040, 3346),
        parent = { FALADOR }),

    /**
     * Ardougne pottery workshop, containing four shaping wheels and one firing oven.
     */
    ARDOUGNE_POTTERY(
        inside = Position(2590, 3325),
        area = SimpleBoxArea.of(2582, 3317, 2599, 3331),
        parent = { ARDOUGNE }),

    /**
     * Lumbridge Castle's first-floor workroom, containing one spinning wheel.
     */
    LUMBRIDGE_SPINNING_WHEEL(
        inside = Position(3209, 3213, 1),
        area = SimpleBoxArea.of(3205, 3208, 3213, 3216, 1),
        parent = { LUMBRIDGE }),

    /**
     * House south of Falador, containing one ground-floor spinning wheel.
     */
    FALADOR_SPINNING_WHEEL(
        inside = Position(2980, 3314),
        area = SimpleBoxArea.of(2977, 3310, 2985, 3318),
        parent = { FALADOR }),

    /**
     * Western first-floor Gnome Stronghold workroom, containing one spinning wheel.
     */
    GNOME_STRONGHOLD_WEST_SPINNING(
        inside = Position(2474, 3398, 1),
        area = SimpleBoxArea.of(2471, 3394, 2479, 3402, 1),
        parent = { GNOME_STRONGHOLD }),

    /**
     * Eastern first-floor Gnome Stronghold workroom, containing one spinning wheel.
     */
    GNOME_STRONGHOLD_EAST_SPINNING(
        inside = Position(2486, 3401, 1),
        area = SimpleBoxArea.of(2483, 3397, 2491, 3405, 1),
        parent = { GNOME_STRONGHOLD }),

    /**
     * Northern first-floor Gnome Stronghold workroom, containing one spinning wheel.
     */
    GNOME_STRONGHOLD_NORTH_SPINNING(
        inside = Position(2487, 3410, 1),
        area = SimpleBoxArea.of(2484, 3406, 2492, 3414, 1),
        parent = { GNOME_STRONGHOLD }),

    /**
     * Falador West bank work area, containing 5 usable bank booths. Portable inventory production uses free
     * local tiles after arrival.
     */
    FALADOR_WEST_BANK_WORK_AREA(
        inside = Position(2945, 3368),
        area = SimpleBoxArea.of(2939, 3355, 2952, 3374),
        parent = { FALADOR }),

    /**
     * Falador East bank work area, containing 6 usable bank booths. Portable inventory production uses free
     * local tiles after arrival.
     */
    FALADOR_EAST_BANK_WORK_AREA(
        inside = Position(3013, 3355),
        area = SimpleBoxArea.of(3008, 3350, 3019, 3362),
        parent = { FALADOR }),

    /**
     * Catherby bank work area, containing 4 usable bank booths. Portable inventory production uses free local
     * tiles after arrival.
     */
    CATHERBY_BANK_WORK_AREA(
        inside = Position(2809, 3441),
        area = SimpleBoxArea.of(2804, 3434, 2815, 3446),
        parent = { CATHERBY }),

    /**
     * Ardougne North bank work area, containing 3 usable bank booths. Portable inventory production uses free
     * local tiles after arrival.
     */
    ARDOUGNE_NORTH_BANK_WORK_AREA(
        inside = Position(2618, 3332),
        area = SimpleBoxArea.of(2612, 3328, 2623, 3341),
        parent = { ARDOUGNE }),

    /**
     * Ardougne South bank work area, containing 2 usable bank booths. Portable inventory production uses free
     * local tiles after arrival.
     */
    ARDOUGNE_SOUTH_BANK_WORK_AREA(
        inside = Position(2655, 3283),
        area = SimpleBoxArea.of(2651, 3279, 2661, 3289),
        parent = { ARDOUGNE }),

    /**
     * Yanille bank work area, containing 3 usable bank booths. Portable inventory production uses free local
     * tiles after arrival.
     */
    YANILLE_BANK_WORK_AREA(
        inside = Position(2613, 3094),
        area = SimpleBoxArea.of(2608, 3089, 2618, 3098),
        parent = { YANILLE }),

    /**
     * Port Phasmatys bank work area, containing 4 usable bank booths. Portable inventory production uses free
     * local tiles after arrival.
     */
    PORT_PHASMATYS_BANK_WORK_AREA(
        inside = Position(3690, 3466),
        area = SimpleBoxArea.of(3684, 3461, 3695, 3469),
        parent = { PORT_PHASMATYS }),

    /**
     * Southeast Varrock tea seller, containing one tea stall for level-five Thieving.
     */
    VARROCK_TEA_STALL(
        inside = Position(3268, 3410),
        area = SimpleBoxArea.of(3265, 3406, 3273, 3414),
        parent = { VARROCK }),

    /**
     * Open ground south of the deep Wilderness pirate hut, containing two ground-floor rogues for level-32
     * pickpocketing. The camp avoids the separate Rogue Castle's stairs; Edgeville supplies banking.
     */
    WILDERNESS_ROGUE_CAMP(
        inside = Position(3077, 3913),
        area = SimpleBoxArea.of(3070, 3903, 3086, 3922),
        parent = { WILDERNESS }, bankZone = { EDGEVILLE }),

    /**
     * Marim's ground-floor market streets, containing one crafting stall, one magic stall and one scimitar stall.
     * The arrival and all three stall approaches share the same walkable street component in the revision-377 map.
     */
    APE_ATOLL_MARKET_STALLS(
        inside = Position(2758, 2773),
        area = SimpleBoxArea.of(2753, 2765, 2785, 2798),
        parent = { APE_ATOLL }),

    /**
     * Keldagrim's underground canal-side market room, containing one crafting stall.
     * Rellekka remains the mainland travel and banking parent for this underground activity.
     */
    KELDAGRIM_CRAFTING_STALL(
        inside = Position(2885, 10206),
        area = SimpleBoxArea.of(2882, 10202, 2891, 10211),
        parent = { RELLEKKA }),

    /**
     * Rellekka's market square, containing three fish stalls and two fur stalls.
     */
    RELLEKKA_MARKET_STALLS(
        inside = Position(2640, 3676),
        area = SimpleBoxArea.of(2634, 3668, 2651, 3684),
        parent = { RELLEKKA }),

    /**
     * Miscellania's market, containing two fish stalls and one vegetable stall.
     */
    MISCELLANIA_MARKET_STALLS(
        inside = Position(2516, 3863),
        area = SimpleBoxArea.of(2509, 3858, 2523, 3870),
        parent = { MISCELLANIA_AND_ETCETERIA }),

    /**
     * Etceteria's market, containing one fish stall and one vegetable stall.
     */
    ETCETERIA_MARKET_STALLS(
        inside = Position(2602, 3876),
        area = SimpleBoxArea.of(2596, 3872, 2610, 3880),
        parent = { MISCELLANIA_AND_ETCETERIA }),

    /**
     * Eastern Ardougne house, containing one ground-floor ten-coin chest.
     */
    ARDOUGNE_EAST_CHEST(
        inside = Position(2673, 3308),
        area = SimpleBoxArea.of(2669, 3303, 2677, 3311),
        parent = { ARDOUGNE }),

    /**
     * Eastern Ardougne upstairs room, containing one nature-rune chest and one fifty-coin chest.
     */
    ARDOUGNE_EAST_CHEST_ROOM(
        inside = Position(2671, 3300, 1),
        area = SimpleBoxArea.of(2667, 3295, 2675, 3305, 1),
        parent = { ARDOUGNE }),

    /**
     * Ardougne Castle's southern upstairs chest room, containing one level-72 chest.
     */
    ARDOUGNE_CASTLE_SOUTH_CHEST(
        inside = Position(2587, 3291, 1),
        area = SimpleBoxArea.of(2584, 3287, 2592, 3295, 1),
        parent = { ARDOUGNE }),

    /**
     * Ardougne Castle's northern upstairs chest room, containing one level-72 chest.
     */
    ARDOUGNE_CASTLE_NORTH_CHEST(
        inside = Position(2587, 3302, 1),
        area = SimpleBoxArea.of(2584, 3298, 2592, 3306, 1),
        parent = { ARDOUGNE }),

    /**
     * Rellekka's northern sandpit, containing one sand source for owned buckets.
     */
    RELLEKKA_SAND_PIT(
        inside = Position(2667, 3706),
        area = SimpleBoxArea.of(2663, 3703, 2671, 3711),
        parent = { RELLEKKA }),

    /**
     * Southern Zanaris sandpit, containing one sand source for owned buckets.
     */
    ZANARIS_SAND_PIT(
        inside = Position(2438, 4442),
        area = SimpleBoxArea.of(2435, 4438, 2443, 4446),
        parent = { ZANARIS }),

    /**
     * Farm beside Lumbridge mill, containing one milkable Dairy Cow object.
     */
    LUMBRIDGE_DAIRY_COW(
        inside = Position(3171, 3317),
        area = SimpleBoxArea.of(3168, 3313, 3176, 3321),
        parent = { LUMBRIDGE }),

    /**
     * Eastern Lumbridge cattle field, containing two milkable Dairy Cow objects.
     */
    EAST_LUMBRIDGE_DAIRY_COW(
        inside = Position(3251, 3275),
        area = SimpleBoxArea.of(3248, 3271, 3256, 3279),
        parent = { LUMBRIDGE }),

    /**
     * Cattle field north of Ardougne, containing one milkable Dairy Cow object.
     */
    ARDOUGNE_DAIRY_COW(
        inside = Position(2673, 3351),
        area = SimpleBoxArea.of(2670, 3347, 2678, 3355),
        parent = { ARDOUGNE }),

    /**
     * Eastern Rellekka cattle enclosure, containing one milkable Dairy Cow object.
     */
    RELLEKKA_DAIRY_COW(
        inside = Position(2677, 3670),
        area = SimpleBoxArea.of(2674, 3666, 2682, 3674),
        parent = { RELLEKKA }),

    /**
     * Cattle field north of Yanille, containing one milkable Dairy Cow object.
     */
    YANILLE_DAIRY_COW(
        inside = Position(2584, 3122),
        area = SimpleBoxArea.of(2581, 3118, 2589, 3126),
        parent = { YANILLE }),

    /**
     * Cattle field north of Seers' Village, containing one milkable Dairy Cow object.
     */
    SEERS_VILLAGE_DAIRY_COW(
        inside = Position(2738, 3560),
        area = SimpleBoxArea.of(2735, 3556, 2743, 3564),
        parent = { SEERS_VILLAGE }),

    /**
     * Western Yanille house, containing one sink for filling owned containers.
     */
    YANILLE_WATER(
        inside = Position(2566, 3094),
        area = SimpleBoxArea.of(2562, 3089, 2570, 3097),
        parent = { YANILLE }),

    /**
     * House beside Ardougne's north bank, containing one sink for filling owned containers.
     */
    ARDOUGNE_WATER(
        inside = Position(2616, 3325),
        area = SimpleBoxArea.of(2613, 3321, 2621, 3329),
        parent = { ARDOUGNE }),

    /**
     * Western Seers' Village house, containing one sink for filling owned containers.
     */
    SEERS_VILLAGE_WATER(
        inside = Position(2700, 3472),
        area = SimpleBoxArea.of(2695, 3468, 2703, 3476),
        parent = { SEERS_VILLAGE }),

    /**
     * Flax field southwest of Rellekka, containing twenty pickable flax plants.
     */
    RELLEKKA_FLAX_1(
        inside = Position(2630, 3636),
        area = SimpleBoxArea.of(2611, 3622, 2644, 3649),
        parent = { RELLEKKA }),

    /**
     * Eastern Gnome Stronghold flax field by the spinning houses, containing twenty pickable plants.
     */
    GNOME_STRONGHOLD_FLAX_1(
        inside = Position(2481, 3399),
        area = SimpleBoxArea.of(2463, 3389, 2498, 3415),
        parent = { GNOME_STRONGHOLD }),

    /**
     * Southwestern Gnome Stronghold flax field, containing eleven pickable plants.
     */
    GNOME_STRONGHOLD_FLAX_2(
        inside = Position(2408, 3387),
        area = SimpleBoxArea.of(2400, 3382, 2416, 3393),
        parent = { GNOME_STRONGHOLD }),

    /**
     * Western Gnome Stronghold flax field, containing eleven pickable plants.
     */
    GNOME_STRONGHOLD_FLAX_3(
        inside = Position(2422, 3400),
        area = SimpleBoxArea.of(2416, 3394, 2429, 3408),
        parent = { GNOME_STRONGHOLD }),

    /**
     * Central Gnome Stronghold flax field, containing seven pickable plants.
     */
    GNOME_STRONGHOLD_FLAX_4(
        inside = Position(2446, 3403),
        area = SimpleBoxArea.of(2439, 3393, 2455, 3411),
        parent = { GNOME_STRONGHOLD }),

    /**
     * Western Gnome Stronghold pond-side flax field, containing five pickable plants.
     */
    GNOME_STRONGHOLD_FLAX_5(
        inside = Position(2400, 3419),
        area = SimpleBoxArea.of(2392, 3415, 2408, 3427),
        parent = { GNOME_STRONGHOLD }),

    /**
     * Northwestern Gnome Stronghold flax field, containing five pickable plants.
     */
    GNOME_STRONGHOLD_FLAX_6(
        inside = Position(2408, 3441),
        area = SimpleBoxArea.of(2403, 3435, 2414, 3448),
        parent = { GNOME_STRONGHOLD }),

    /**
     * North-central Gnome Stronghold flax field, containing four pickable plants.
     */
    GNOME_STRONGHOLD_FLAX_7(
        inside = Position(2428, 3425),
        area = SimpleBoxArea.of(2423, 3419, 2433, 3429),
        parent = { GNOME_STRONGHOLD }),

    /**
     * Musa Point's jungle west of the banana plantation, containing three monkeys for beginner combat and
     * monkey assignments.
     */
    MUSA_POINT_MONKEYS(
        inside = Position(2876, 3155),
        area = SimpleBoxArea.of(2868, 3143, 2885, 3167),
        parent = { KARAMJA }, bankZone = { DRAYNOR }),

    /**
     * The south-western desert lizard group, containing four lizards for both native lizard assignment types.
     * Trips require owned ice coolers.
     */
    DESERT_LIZARDS_SOUTHWEST(
        inside = Position(3386, 3016),
        area = SimpleBoxArea.of(3379, 3008, 3394, 3024),
        parent = { KHARIDIAN_DESERT }, bankZone = { AL_KHARID }),

    /**
     * The north-western desert lizard group, containing four lizards for both native lizard assignment types.
     * Trips require owned ice coolers.
     */
    DESERT_LIZARDS_NORTHWEST(
        inside = Position(3387, 3067),
        area = SimpleBoxArea.of(3380, 3060, 3395, 3075),
        parent = { KHARIDIAN_DESERT }, bankZone = { AL_KHARID }),

    /**
     * The central desert lizard group, containing 18 lizards for both native lizard assignment types. Trips
     * require owned ice coolers.
     */
    DESERT_LIZARDS_CENTRAL(
        inside = Position(3413, 3049),
        area = SimpleBoxArea.of(3395, 3024, 3431, 3074),
        parent = { KHARIDIAN_DESERT }, bankZone = { AL_KHARID }),

    /**
     * The eastern desert lizard group, containing 17 lizards for both native lizard assignment types. Trips
     * require owned ice coolers.
     */
    DESERT_LIZARDS_EAST(
        inside = Position(3441, 3039),
        area = SimpleBoxArea.of(3430, 3007, 3453, 3072),
        parent = { KHARIDIAN_DESERT }, bankZone = { AL_KHARID }),

    /**
     * The western shore of the River Elid, containing three crocodiles with land approaches on this side of the
     * river.
     */
    ELID_CROCODILES_WEST_BANK(
        inside = Position(3259, 2860),
        area = SimpleBoxArea.of(3253, 2846, 3267, 2874),
        parent = { KHARIDIAN_DESERT }, bankZone = { AL_KHARID }),

    /**
     * The eastern shore of the River Elid, containing two crocodiles with land approaches on this side of the
     * river.
     */
    ELID_CROCODILES_EAST_BANK(
        inside = Position(3269, 2860),
        area = SimpleBoxArea.of(3263, 2849, 3275, 2872),
        parent = { KHARIDIAN_DESERT }, bankZone = { AL_KHARID }),

    /**
     * Trollheim mountain slopes containing eight mountain trolls for Troll assignments; the local footprint
     * follows the connected combat ground.
     */
    TROLLHEIM_MOUNTAIN_TROLLS(
        inside = Position(2850, 3679),
        area = SimpleBoxArea.of(2838, 3668, 2862, 3690),
        parent = { BURTHORPE }),

    /**
     * The Karamja harpie habitat, containing 14 harpie bug swarms. Assignment trips require a worn, lit bug
     * lantern.
     */
    KARAMJA_HARPIE_SWARMS(
        inside = Position(2872, 3114),
        area = SimpleBoxArea.of(2849, 3100, 2895, 3129),
        parent = { KARAMJA }, bankZone = { DRAYNOR }),

    /**
     * The icy chamber beneath Asgarnia, containing 14 ice warriors and ice giants for their respective
     * assignments.
     */
    ASGARNIAN_ICE(
        inside = Position(3055, 9583),
        area = SimpleBoxArea.of(3034, 9564, 3072, 9597),
        parent = { PORT_SARIM }, bankZone = { DRAYNOR }),

    /**
     * The chamber beneath the Legends' Guild containing 19 shadow warriors for Shadow Warrior assignments.
     */
    LEGENDS_SHADOW(
        inside = Position(2700, 9769),
        area = SimpleBoxArea.of(2684, 9745, 2717, 9789),
        parent = { ARDOUGNE }),

    /**
     * The red-dragon section of Brimhaven Dungeon, containing 14 red dragons. Combat preparation retains
     * dragonfire protection.
     */
    BRIMHAVEN_RED(
        inside = Position(2711, 9515),
        area = SimpleBoxArea.of(2687, 9491, 2740, 9537),
        parent = { BRIMHAVEN }, bankZone = { ARDOUGNE }),

    /**
     * Entrana Dungeon's room containing two greater demons; the legacy enum name is retained for saved sessions.
     * Entry is one-way from Entrana, and its magic-door exit leads into deep Wilderness.
     */
    TAVERLY_GREATER(
        inside = Position(2863, 9750),
        area = SimpleBoxArea.of(2852, 9739, 2874, 9763),
        parent = { ENTRANA }),

    /**
     * The western hellhound section of Taverley Dungeon, containing 13 hellhounds beyond the dusty-key gate.
     */
    TAVERLY_HELLHOUND(
        inside = Position(2862, 9836),
        area = SimpleBoxArea.of(2844, 9811, 2881, 9862),
        parent = { TAVERLY }),

    /**
     * The metal-dragon section of Brimhaven Dungeon, containing 17 iron and steel dragons. Trips require an
     * anti-dragon shield, antifire and strong food.
     */
    BRIMHAVEN_METAL(
        inside = Position(2724, 9439),
        area = SimpleBoxArea.of(2693, 9412, 2750, 9472),
        parent = { BRIMHAVEN }, bankZone = { ARDOUGNE }),

    /**
     * Mort'ton's surface shade habitat, containing 25 dormant Loar shadows that awaken through their native
     * encounter before Shade kills count.
     */
    MORTTON_SURFACE(
        inside = Position(3495, 3294),
        area = SimpleBoxArea.of(3469, 3262, 3521, 3326),
        parent = { CANIFIS }),

    /**
     * The Killerwatt plane above Draynor Manor, containing 26 dormant electrical forms. Assignment trips
     * require insulated boots.
     */
    KILLERWATT_REALM(
        inside = Position(2657, 5213, 2),
        area = SimpleBoxArea.of(2637, 5192, 2680, 5235, 2),
        parent = { DRAYNOR }),

    /**
     * The Braindeath cellar, containing 11 fever spiders. Assignment trips require Slayer gloves.
     */
    BRAINDEATH_FEVER(
        inside = Position(2150, 5099),
        area = SimpleBoxArea.of(2135, 5088, 2166, 5110),
        parent = { PORT_PHASMATYS }),

    /**
     * The dust-devil section of the Smoke Dungeon, containing 19 dust devils. Assignment trips require a worn
     * facemask.
     */
    SMOKE_DUNGEON(
        inside = Position(3237, 9374),
        area = SimpleBoxArea.of(3210, 9344, 3264, 9405),
        parent = { KHARIDIAN_DESERT }, bankZone = { AL_KHARID }),

    /**
     * The skeletal-wyvern cavern beneath the Asgarnian ice dungeon, containing eight skeletal wyverns. Trips
     * require an elemental shield and strong food.
     */
    ASGARNIAN_WYVERNS(
        inside = Position(3046, 9547),
        area = SimpleBoxArea.of(3020, 9534, 3073, 9561),
        parent = { PORT_SARIM }, bankZone = { DRAYNOR }),

    /**
     * The dark-beast cavern, containing 11 dark beasts for high-level assignments with owned strong food.
     */
    DARK_BEAST_CAVE(
        inside = Position(1993, 4649),
        area = SimpleBoxArea.of(1983, 4627, 2007, 4671),
        parent = { ARDOUGNE }),

    /**
     * Mudskipper Point shoreline containing three ominous fishing spots. Owned fishing explosives lure Mogres
     * through the native encounter; there are no permanent Mogre spawns.
     */
    MUDSKIPPER_MOGRES(
        inside = Position(2996, 3111),
        area = SimpleBoxArea.of(2980, 3104, 3007, 3124),
        parent = { PORT_SARIM }, bankZone = { DRAYNOR }),

    /**
     * Lumbridge Swamp Caves containing 21 cave bugs, 17 cave slimes and seven dormant wall-beast holes. Trips
     * retain an enclosed lit source and the protection or poison cure required by the assignment.
     */
    LUMBRIDGE_SWAMP_CAVES(
        inside = Position(3148, 9571),
        area = SimpleBoxArea.of(3142, 9536, 3262, 9603),
        parent = { LUMBRIDGE }),

    /**
     * The Lighthouse basement, containing 22 dagannoths. Arrival uses the walkable landing south of the ladder
     * object.
     */
    LIGHTHOUSE_DAGANNOTHS(
        inside = Position(2515, 10008),
        area = SimpleBoxArea.of(2496, 10002, 2545, 10045),
        parent = { RELLEKKA }),

    /**
     * The northern ghoul group west of Canifis, containing 11 ghouls for Ghoul assignments.
     */
    CANIFIS_NORTH_GHOULS(
        inside = Position(3416, 3513),
        area = SimpleBoxArea.of(3407, 3504, 3425, 3523),
        parent = { CANIFIS }),

    /**
     * The southern ghoul group west of Canifis, containing ten ghouls for Ghoul assignments.
     */
    CANIFIS_SOUTH_GHOULS(
        inside = Position(3429, 3463),
        area = SimpleBoxArea.of(3418, 3453, 3439, 3473),
        parent = { CANIFIS }),

    /**
     * The hill-giant room in Taverley Dungeon, containing five hill giants for ordinary training and
     * assignments before the dusty-key gate.
     */
    TAVERLY_HILL_GIANTS(
        inside = Position(2908, 9737),
        area = SimpleBoxArea.of(2894, 9724, 2922, 9752),
        parent = { TAVERLY }),

    /**
     * The lesser-demon room in Taverley Dungeon, containing five lesser demons for ordinary training and
     * assignments before the dusty-key gate.
     */
    TAVERLY_LESSER_DEMONS(
        inside = Position(2932, 9806),
        area = SimpleBoxArea.of(2918, 9785, 2948, 9822),
        parent = { TAVERLY }),

    /**
     * The western black-demon room in Taverley Dungeon, containing 11 black demons beyond the dusty-key gate.
     */
    TAVERLY_BLACK_DEMONS(
        inside = Position(2868, 9776),
        area = SimpleBoxArea.of(2846, 9755, 2888, 9795),
        parent = { TAVERLY }),

    /**
     * Waterbirth Island's surface beach, containing 19 disguised rock crabs and two aggressive hobgoblins.
     * Readiness accounts for the hobgoblins; the crab script waits for native awakening.
     */
    WATERBIRTH_SURFACE_CRABS(
        inside = Position(2529, 3741),
        area = SimpleBoxArea.of(2492, 3708, 2563, 3776),
        parent = { RELLEKKA }),

    /**
     * The western Waterfall Dungeon chamber, containing three fire giants. Its arrival remains in this room,
     * and trips keep Glarial's amulet equipped.
     */
    WATERFALL_FIRE_GIANTS_WEST(
        inside = Position(2567, 9888),
        area = SimpleBoxArea.of(2562, 9882, 2571, 9893),
        parent = { ARDOUGNE }),

    /**
     * The eastern Waterfall Dungeon chamber, containing seven fire giants. Its arrival remains in this room,
     * and trips keep Glarial's amulet equipped.
     */
    WATERFALL_FIRE_GIANTS_EAST(
        inside = Position(2578, 9892),
        area = SimpleBoxArea.of(2572, 9885, 2584, 9899),
        parent = { ARDOUGNE }),

    /**
     * The southern moss-giant pocket in Brimhaven Dungeon, containing three moss giants with a shared approach
     * inside this room.
     */
    BRIMHAVEN_MOSS_GIANTS_SOUTH(
        inside = Position(2646, 9488),
        area = SimpleBoxArea.of(2636, 9477, 2655, 9500),
        parent = { BRIMHAVEN }, bankZone = { ARDOUGNE }),

    /**
     * The western moss-giant pocket in Brimhaven Dungeon, containing six moss giants on the same connected side
     * of the cavern walls.
     */
    BRIMHAVEN_MOSS_GIANTS_WEST(
        inside = Position(2644, 9533),
        area = SimpleBoxArea.of(2630, 9510, 2658, 9557),
        parent = { BRIMHAVEN }, bankZone = { ARDOUGNE }),

    /**
     * The central moss-giant pocket in Brimhaven Dungeon, containing four moss giants with a shared approach
     * inside this room.
     */
    BRIMHAVEN_MOSS_GIANTS_CENTRAL(
        inside = Position(2671, 9546),
        area = SimpleBoxArea.of(2655, 9538, 2689, 9562),
        parent = { BRIMHAVEN }, bankZone = { ARDOUGNE }),

    /**
     * The narrow moss-giant passage in Brimhaven Dungeon, containing one moss giant kept separate from the
     * adjoining cavern rooms.
     */
    BRIMHAVEN_MOSS_GIANTS_PASSAGE(
        inside = Position(2651, 9566),
        area = SimpleBoxArea.of(2648, 9561, 2655, 9571),
        parent = { BRIMHAVEN }, bankZone = { ARDOUGNE }),

    /**
     * The northern moss-giant pocket in Brimhaven Dungeon, containing six moss giants with a shared approach
     * inside this room.
     */
    BRIMHAVEN_MOSS_GIANTS_NORTH(
        inside = Position(2656, 9587),
        area = SimpleBoxArea.of(2637, 9570, 2685, 9597),
        parent = { BRIMHAVEN }, bankZone = { ARDOUGNE }),

    /**
     * The western chicken pen at Falador's southern farm, containing six chickens. Its arrival is inside this
     * enclosure rather than the adjacent cattle field.
     */
    SOUTH_FALADOR_FARM_WEST_CHICKENS(
        inside = Position(3017, 3289),
        area = SimpleBoxArea.of(3013, 3281, 3021, 3296),
        parent = { FALADOR }),

    /**
     * The eastern chicken enclosure at Falador's southern farm, containing four chickens. Its own arrival
     * avoids targeting across the farm's dividing fences.
     */
    SOUTH_FALADOR_FARM_EAST_CHICKENS(
        inside = Position(3029, 3287),
        area = SimpleBoxArea.of(3025, 3284, 3033, 3290),
        parent = { FALADOR }),

    /**
     * The cattle field at Falador's southern farm, containing ten cows. Its arrival stays inside the cattle
     * enclosure, separate from both chicken pens.
     */
    SOUTH_FALADOR_FARM_CATTLE(
        inside = Position(3034, 3307),
        area = SimpleBoxArea.of(3025, 3298, 3044, 3315),
        parent = { FALADOR }),

    /**
     * Open ground around Catherby's bank and range, providing three 14-fire westward lanes with a clear final
     * step-off tile. Spot reservations are checked only after arrival.
     */
    CATHERBY_FIREMAKING(
        inside = Position(2815, 3447),
        area = SimpleBoxArea.of(2794, 3433, 2819, 3447),
        parent = { CATHERBY }),

    /**
     * Roads west and south-west of Yanille bank, providing three 14-fire westward lanes with a clear final
     * step-off tile. Spot reservations are checked only after arrival.
     */
    YANILLE_FIREMAKING(
        inside = Position(2611, 3087),
        area = SimpleBoxArea.of(2593, 3083, 2614, 3097),
        parent = { YANILLE }),

    /**
     * Roads around Ardougne's northern bank, providing three 14-fire westward lanes with a clear final step-off
     * tile. Spot reservations are checked only after arrival.
     */
    ARDOUGNE_NORTH_FIREMAKING(
        inside = Position(2616, 3342),
        area = SimpleBoxArea.of(2601, 3338, 2626, 3345),
        parent = { ARDOUGNE }),

    /**
     * Open rows around Ardougne's southern bank, providing three 14-fire westward lanes with a clear final
     * step-off tile. Spot reservations are checked only after arrival.
     */
    ARDOUGNE_SOUTH_FIREMAKING(
        inside = Position(2643, 3289),
        area = SimpleBoxArea.of(2629, 3277, 2669, 3299),
        parent = { ARDOUGNE }),

    /**
     * Open ground around Falador's western bank, providing three 14-fire westward lanes with a clear final
     * step-off tile. Spot reservations are checked only after arrival.
     */
    FALADOR_WEST_FIREMAKING(
        inside = Position(2951, 3375),
        area = SimpleBoxArea.of(2937, 3357, 2963, 3381),
        parent = { FALADOR });

    companion object {

        /**
         * Maps every intersected map region to its subzones, including interior regions of large rectangles.
         *
         * This is used as a cheap pre-filter before checking exact tile containment. A bot only scans local subzones
         * for its current region instead of checking every subzone every time it moves.
         */
        val LOCAL: ImmutableSetMultimap<Int, SubZone> = run {
            val map = HashMultimap.create<Int, SubZone>()

            for (zone in entries) {
                for (regionX in (zone.area.southWest.x shr 6)..(zone.area.northEast.x shr 6)) {
                    for (regionY in (zone.area.southWest.y shr 6)..(zone.area.northEast.y shr 6)) {
                        map.put((regionX shl 8) or regionY, zone)
                    }
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
                    if (zone.area.contains(bot.position)) {
                        bot.subZones.add(zone)
                    }
                }
            }
        }
    }

    /**
     * Checks whether a [Bot] is inside this activity or close to its local or external anchor.
     *
     * @param bot The bot to check.
     * @param distance The maximum allowed tile distance.
     * @return `true` if [bot] is inside this activity or within [distance] of [inside] or [entrance].
     */
    fun isWithinDistance(bot: Bot, distance: Int): Boolean {
        if (area.contains(bot.position) || this in bot.subZones || inside.isWithinDistance(bot, distance)) {
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
                CHAOS_DRUID_TOWER !in bot.subZones && CHAOS_DRUID_TOWER_DUNGEON !in bot.subZones
            )
                "the tower door needs level ${ChaosDruidTowerDoorAction.REQUIRED_LEVEL} Thieving" else null

        TAVERLEY_DUNGEON_ZAMORAK_MONKS, TAVERLY_BLACK_DEMONS, TAVERLY_HELLHOUND,
        TAVERLY_DUNGEON_BLUE_DRAGONS ->
            if (this !in bot.subZones && !canEnterTaverleyWest(bot))
                "the western section of Taverley Dungeon needs a dusty key" else null

        EDGEVILLE_DUNGEON_MINE, EDGEVILLE_DUNGEON_HILL_GIANTS ->
            // A bot without a key buys one for a coin.
            if (this !in bot.subZones && !bot.itemTracker.contains(BrassKeyDoor.KEY) &&
                !bot.inventory.contains(Currency.COINS.id) && !bot.bank.contains(Currency.COINS.id)
            )
                "the house door needs a brass key" else null

        WATERFALL_FIRE_GIANTS_WEST, WATERFALL_FIRE_GIANTS_EAST ->
            if (!bot.itemTracker.contains(295)) "the Waterfall entrance needs Glarial's amulet" else null

        else -> null
    }

    /**
     * The item that a bot must carry to get into this subzone, which it keeps when banking.
     */
    val entryItem: Int?
        get() = when (this) {
            TAVERLEY_DUNGEON_ZAMORAK_MONKS, TAVERLY_BLACK_DEMONS, TAVERLY_HELLHOUND,
            TAVERLY_DUNGEON_BLUE_DRAGONS -> TaverleyJailDoor.DUSTY_KEY_GATE.key
            WATERFALL_FIRE_GIANTS_WEST, WATERFALL_FIRE_GIANTS_EAST -> 295
            EDGEVILLE_DUNGEON_MINE, EDGEVILLE_DUNGEON_HILL_GIANTS -> BrassKeyDoor.KEY
            else -> null
        }

    /**
     * Determines if a bot can get into the western section of Taverley Dungeon. Every bot carries the dusty key there,
     * and one without a key buys it for a coin. The web walker still takes the pipe or the strange floor for a bot with
     * the Agility for them, when that is shorter.
     */
    private fun canEnterTaverleyWest(bot: Bot): Boolean =
        bot.itemTracker.contains(TaverleyJailDoor.DUSTY_KEY_GATE.key) ||
                bot.inventory.contains(Currency.COINS.id) || bot.bank.contains(Currency.COINS.id)
}
