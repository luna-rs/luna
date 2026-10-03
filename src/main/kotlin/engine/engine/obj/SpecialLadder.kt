package engine.obj

import io.luna.game.model.Position

/**
 * Ladders that lead somewhere other than straight up or down, keyed by the ladder's position. A destination holding a
 * ladder or trapdoor means landing beside it.
 *
 * Quest and minigame requirements aren't checked because Luna doesn't have that content yet. Ladders added after 2004
 * are paired with the ladder or trapdoor that leads back to them. The Entrana house ladder is here because the tile
 * above one of its sides is walled off from the rest of the floor.
 *
 * @author TheLining
 */
enum class SpecialLadder(val ladder: Position, val destination: Position) {

    // Temple of Ikov trap pit.
    IKOV_TRAP(ladder = Position(2682, 9849), destination = Position(2665, 9849)),

    // Draynor Manor basement (Ernest the Chicken).
    DRAYNOR_MANOR_DOWN(ladder = Position(3092, 3362), destination = Position(3116, 9754)),
    DRAYNOR_MANOR_UP(ladder = Position(3117, 9754), destination = Position(3092, 3361)),

    // Brimstail's cave, Tree Gnome Stronghold.
    BRIMSTAIL_CAVE(ladder = Position(2409, 9818), destination = Position(2410, 3421)),

    // Phoenix Gang hideout, Varrock.
    PHOENIX_GANG_UP(ladder = Position(3244, 9783), destination = Position(3244, 3382)),

    // Lava maze and King Black Dragon lair entrance (the two ladders cross over).
    KBD_ENTRANCE_DOWN(ladder = Position(3017, 3849), destination = Position(3069, 10255)),
    KBD_ENTRANCE_UP(ladder = Position(3069, 10256), destination = Position(3016, 3849)),
    LAVA_MAZE_DOWN(ladder = Position(3069, 3856), destination = Position(3016, 10249)),
    LAVA_MAZE_UP(ladder = Position(3017, 10249), destination = Position(3070, 3856)),

    // Wizards' Tower basement.
    WIZARDS_TOWER_DOWN(ladder = Position(3104, 3162), destination = Position(3104, 9576)),
    WIZARDS_TOWER_UP(ladder = Position(3103, 9576), destination = Position(3105, 3162)),

    // Observatory dungeon.
    OBSERVATORY_DUNGEON_DOWN(ladder = Position(2444, 3191), destination = Position(2420, 9459)),
    OBSERVATORY_DUNGEON_UP(ladder = Position(2420, 9460), destination = Position(2444, 3192)),
    OBSERVATORY_DOME_UP(ladder = Position(2423, 9439), destination = Position(2440, 3165)),
    OBSERVATORY_DOME_DOWN(ladder = Position(2440, 3164), destination = Position(2423, 9438)),

    // Entrana dungeon (one way, the way out is a portal).
    ENTRANA_DUNGEON(ladder = Position(2820, 3374), destination = Position(2822, 9774)),

    // Lady Lumbridge at Port Sarim (Dragon Slayer).
    LADY_LUMBRIDGE_DOWN(ladder = Position(3049, 3208, 1), destination = Position(3047, 9640, 1)),
    LADY_LUMBRIDGE_UP_1(ladder = Position(3049, 9640, 1), destination = Position(3047, 3207, 1)),
    LADY_LUMBRIDGE_UP_2(ladder = Position(3049, 9640, 2), destination = Position(3047, 3207, 1)),
    LADY_LUMBRIDGE_UP_3(ladder = Position(3049, 9640, 3), destination = Position(3047, 3207, 1)),

    // Ogre enclave (Watchtower).
    TOBAN_DOWN(ladder = Position(2575, 3029), destination = Position(2500, 2988)),

    // Brimhaven Agility Arena.
    AGILITY_ARENA_DOWN(ladder = Position(2809, 3194), destination = Position(2805, 9589, 3)),
    AGILITY_ARENA_UP(ladder = Position(2805, 9590, 3), destination = Position(2808, 3193)),

    // Rellekka, Swensen's maze (The Fremennik Trials).
    SWENSEN_MAZE_DOWN(ladder = Position(2644, 3657), destination = Position(2631, 10004)),
    SWENSEN_MAZE_ENTRANCE(ladder = Position(2631, 10005), destination = Position(2644, 3658)),
    SWENSEN_MAZE_EXIT(ladder = Position(2665, 10037), destination = Position(2649, 3661)),

    // Rellekka, Thorvald's battleground (The Fremennik Trials).
    THORVALD_BATTLEGROUND_UP(ladder = Position(2672, 10099, 2), destination = Position(2666, 3694)),

    // Haunted Mine, whose levels are copies of each other side by side.
    HAUNTED_MINE_DOWN_1(ladder = Position(3413, 9633), destination = Position(2773, 4577)),
    HAUNTED_MINE_DOWN_2(ladder = Position(3422, 9625), destination = Position(2782, 4569)),
    HAUNTED_MINE_UP_1(ladder = Position(2773, 4577), destination = Position(3413, 9633)),
    HAUNTED_MINE_UP_2(ladder = Position(2782, 4569), destination = Position(3422, 9625)),
    HAUNTED_MINE_DOWN_3(ladder = Position(2797, 4599), destination = Position(2733, 4535)),
    HAUNTED_MINE_DOWN_4(ladder = Position(2798, 4567), destination = Position(2734, 4503)),
    HAUNTED_MINE_UP_3(ladder = Position(2733, 4535), destination = Position(2797, 4599)),
    HAUNTED_MINE_UP_4(ladder = Position(2734, 4503), destination = Position(2798, 4567)),
    HAUNTED_MINE_DOWN_5(ladder = Position(2696, 4497), destination = Position(2760, 4497)),
    HAUNTED_MINE_DOWN_6(ladder = Position(2710, 4540), destination = Position(2774, 4540)),
    HAUNTED_MINE_DOWN_7(ladder = Position(2725, 4486), destination = Position(2789, 4486)),
    HAUNTED_MINE_DOWN_8(ladder = Position(2732, 4529), destination = Position(2796, 4529)),
    HAUNTED_MINE_UP_5(ladder = Position(2760, 4497), destination = Position(2696, 4497)),
    HAUNTED_MINE_UP_6(ladder = Position(2774, 4540), destination = Position(2710, 4540)),
    HAUNTED_MINE_UP_7(ladder = Position(2789, 4486), destination = Position(2725, 4486)),
    HAUNTED_MINE_UP_8(ladder = Position(2796, 4529), destination = Position(2732, 4529)),

    // Jaldraocht Pyramid (Desert Treasure), from the top down to the altar room.
    JALDRAOCHT_PYRAMID_DOWN_1(ladder = Position(3233, 2897), destination = Position(2913, 4953, 3)),
    JALDRAOCHT_PYRAMID_UP_1(ladder = Position(2913, 4953, 3), destination = Position(3233, 2897)),
    JALDRAOCHT_PYRAMID_DOWN_2(ladder = Position(2909, 4964, 3), destination = Position(2845, 4964, 2)),
    JALDRAOCHT_PYRAMID_UP_2(ladder = Position(2845, 4964, 2), destination = Position(2909, 4964, 3)),
    JALDRAOCHT_PYRAMID_DOWN_3(ladder = Position(2846, 4973, 2), destination = Position(2782, 4973, 1)),
    JALDRAOCHT_PYRAMID_UP_3(ladder = Position(2782, 4973, 1), destination = Position(2846, 4973, 2)),
    JALDRAOCHT_PYRAMID_DOWN_4(ladder = Position(2784, 4941, 1), destination = Position(3232, 9293)),
    JALDRAOCHT_PYRAMID_UP_4(ladder = Position(3232, 9293), destination = Position(2784, 4941, 1)),

    // Dagannoth Kings' lair.
    DAGANNOTH_KINGS_DOWN(ladder = Position(1911, 4367), destination = Position(2899, 4449)),
    DAGANNOTH_KINGS_UP(ladder = Position(2899, 4449), destination = Position(1911, 4367)),

    // Goblin Village cellar, which has two copies.
    GOBLIN_VILLAGE_CELLAR_DOWN(ladder = Position(2960, 3507), destination = Position(2981, 9916)),
    GOBLIN_VILLAGE_CELLAR_UP_1(ladder = Position(2981, 9916), destination = Position(2960, 3507)),
    GOBLIN_VILLAGE_CELLAR_UP_2(ladder = Position(2981, 9876), destination = Position(2960, 3507)),

    // Entrana house, whose upper floor is only connected to the ladder's east side.
    ENTRANA_HOUSE_UP(ladder = Position(2816, 3352), destination = Position(2817, 3352, 1)),

    // Ladders out to the trapdoor above.
    CANIFIS_CELLAR_UP(ladder = Position(3477, 9846), destination = Position(3494, 3464)),
    WEREWOLF_AGILITY_COURSE_UP(ladder = Position(3549, 9864), destination = Position(3543, 3462)),
    ECTOFUNTUS_UP(ladder = Position(3668, 9888, 3), destination = Position(3653, 3519)),
    MOURNER_HEADQUARTERS_UP(ladder = Position(2044, 4650), destination = Position(2542, 3327));

    companion object {

        /**
         * The special ladders by position.
         */
        private val ALL = values().associateBy { it.ladder }

        /**
         * Returns the special ladder at [position], or `null` if there isn't one.
         */
        fun forPosition(position: Position) = ALL[position]
    }
}
