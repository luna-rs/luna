package game.skill.runecrafting.enterAltar

import api.bot.zone.SubZone
import io.luna.game.model.Position

/**
 * Represents a Runecrafting altar and its related world data.
 *
 * Each altar defines the outside ruins object, required access items, exit portal, inside position, outside exit
 * position, and the bot subzone used while crafting runes inside the altar area.
 *
 * @author lare96
 */
enum class Altar(
    val outsideId: Int,
    val talisman: Int,
    val tiara: Int,
    val portal: Int,
    val enter: Position,
    val exit: Position,
    val zone: SubZone
) {

    AIR(
        outsideId = 2452,
        talisman = 1438,
        tiara = 5527,
        portal = 2465,
        enter = Position(2841, 4829),
        exit = Position(2983, 3292),
        zone = SubZone.AIR_ALTAR
    ),

    MIND(
        outsideId = 2453,
        talisman = 1448,
        tiara = 5529,
        portal = 2466,
        enter = Position(2793, 4828),
        exit = Position(2980, 3514),
        zone = SubZone.MIND_ALTAR
    ),

    WATER(
        outsideId = 2454,
        talisman = 1444,
        tiara = 5531,
        portal = 2467,
        enter = Position(2726, 4832),
        exit = Position(3187, 3166),
        zone = SubZone.WATER_ALTAR
    ),

    EARTH(
        outsideId = 2455,
        talisman = 1440,
        tiara = 5535,
        portal = 2468,
        enter = Position(2655, 4830),
        exit = Position(3304, 3474),
        zone = SubZone.EARTH_ALTAR
    ),

    FIRE(
        outsideId = 2456,
        talisman = 1442,
        tiara = 5537,
        portal = 2469,
        enter = Position(2574, 4849),
        exit = Position(3311, 3256),
        zone = SubZone.FIRE_ALTAR
    ),

    BODY(
        outsideId = 2457,
        talisman = 1446,
        tiara = 5533,
        portal = 2470,
        enter = Position(2524, 4825),
        exit = Position(3051, 3445),
        zone = SubZone.BODY_ALTAR
    ),

    COSMIC(
        outsideId = 2458,
        talisman = 1454,
        tiara = 5539,
        portal = 2471,
        enter = Position(2142, 4813),
        exit = Position(2408, 4379),
        zone = SubZone.COSMIC_ALTAR
    ),

    CHAOS(
        outsideId = 2461,
        talisman = 1452,
        tiara = 5543,
        portal = 2474,
        enter = Position(2268, 4842),
        exit = Position(3058, 3591),
        zone = SubZone.CHAOS_ALTAR
    ),

    NATURE(
        outsideId = 2460,
        talisman = 1462,
        tiara = 5541,
        portal = 2473,
        enter = Position(2400, 4835),
        exit = Position(2867, 3019),
        zone = SubZone.NATURE_ALTAR
    ),

    LAW(
        outsideId = 2459,
        talisman = 1458,
        tiara = 5545,
        portal = 2472,
        enter = Position(2464, 4818),
        exit = Position(2858, 3379),
        zone = SubZone.LAW_ALTAR
    ),

    DEATH(
        outsideId = 2462,
        talisman = 1456,
        tiara = 5547,
        portal = 2475,
        enter = Position(2208, 4830),
        exit = Position(3222, 3222),
        zone = SubZone.DEATH_ALTAR
    );

    companion object {

        /**
         * All defined Runecrafting altars.
         */
        val ALL = entries.toSet()

        /**
         * Maps outside ruins object ids to their corresponding [Altar].
         */
        val OBJECT_TO_ALTAR = entries.associateBy { it.outsideId }

        /**
         * Maps talisman item ids to their corresponding [Altar].
         */
        val TALISMAN_TO_ALTAR = entries.associateBy { it.talisman }

        /**
         * Maps tiara item ids to their corresponding [Altar].
         */
        val TIARA_TO_ALTAR = entries.associateBy { it.tiara }

        /**
         * Maps exit portal object ids to their corresponding [Altar].
         */
        val PORTAL_TO_ALTAR = entries.associateBy { it.portal }
    }
}