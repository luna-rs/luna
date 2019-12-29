package world.player.skill.runecrafting.enterAltar

import io.luna.game.model.Position

/**
 * An enum representing a Runecrafting altar.
 */
enum class Altar(val id: Int,
                 val talisman: Int,
                 val tiara: Int,
                 val portal: Int,
                 val enter: Position,
                 val exit: Position) {

    AIR(id = 2452,
        talisman = 1438,
        tiara = 5527,
        portal = 2465,
        enter = Position(2841, 4829),
        exit = Position(2983, 3292)),
    MIND(id = 2453,
         talisman = 1448,
         tiara = 5529,
         portal = 2466,
         enter = Position(2793, 4828),
         exit = Position(2980, 3514)),
    WATER(id = 2454,
          talisman = 1444,
          tiara = 5531,
          portal = 2467,
          enter = Position(2726, 4832),
          exit = Position(3187, 3166)),
    EARTH(id = 2455,
          talisman = 1440,
          tiara = 5535,
          portal = 2468,
          enter = Position(2655, 4830),
          exit = Position(3304, 3474)),
    FIRE(id = 2456,
         talisman = 1442,
         tiara = 5537,
         portal = 2469,
         enter = Position(2574, 4849),
         exit = Position(3311, 3256)),
    BODY(id = 2457,
         talisman = 1446,
         tiara = 5533,
         portal = 2470,
         enter = Position(2524, 4825),
         exit = Position(3051, 3445)),
    COSMIC(id = 2458,
           talisman = 1454,
           tiara = 5539,
           portal = 2471,
           enter = Position(2142, 4813),
           exit = Position(2408, 4379)),
    CHAOS(id = 2461,
          talisman = 1452,
          tiara = 5543,
          portal = 2474,
          enter = Position(2268, 4842),
          exit = Position(3058, 3591)),
    NATURE(id = 2460,
           talisman = 1462,
           tiara = 5541,
           portal = 2473,
           enter = Position(2400, 4835),
           exit = Position(2867, 3019)),
    LAW(id = 2459,
        talisman = 1458,
        tiara = 5545,
        portal = 2472,
        enter = Position(2464, 4818),
        exit = Position(2858, 3379)),
    DEATH(id = 2462,
          talisman = 1456,
          tiara = 5547,
          portal = 2475,
          enter = Position(2208, 4830),
          exit = Position(3222, 3222));

    companion object {

        /**
         * Mappings of [Altar.talisman] to [Altar] instances.
         */
        val TALISMAN_TO_ALTAR = values().associateBy { it.talisman }

        /**
         * Mappings of [Altar.tiara] to [Altar] instances.
         */
        val TIARA_TO_ALTAR = values().associateBy { it.tiara }

        /**
         * Mappings of [Altar.portal] to [Altar] instances.
         */
        val PORTAL_TO_ALTAR = values().associateBy { it.portal }
    }
}