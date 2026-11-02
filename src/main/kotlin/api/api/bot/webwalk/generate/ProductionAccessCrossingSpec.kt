package api.bot.webwalk.generate

import api.bot.webwalk.model.Requirements
import api.bot.webwalk.model.ItemRequirement
import api.bot.webwalk.walk.crossing.ProductionAccessCrossing
import game.content.shiloFurnace.ShiloFurnace
import game.skill.thieving.lockedDoor.ArdougneHouseDoorAction

/**
 * The source-gated furnace and Ardougne house doors separating existing production facilities from their roads.
 * Each requirement applies only on entry; none of these doors charges or checks a level when leaving.
 *
 * @author lare96
 */
object ProductionAccessCrossingSpec {

    val SHILO_FURNACE = CrossingSpec(ProductionAccessCrossing.ID, setOf(ShiloFurnace.DOOR),
        Requirements(coins = ShiloFurnace.FARE), reverseRequirements = Requirements.NONE, cost = 14)

    val ARDOUGNE_CHEST_HOUSE = CrossingSpec(ProductionAccessCrossing.ID,
        setOf(ArdougneHouseDoorAction.Lock.CHEST_HOUSE.door),
        Requirements(flags = setOf(ProductionAccessCrossing.CHEST_ADMISSION)),
        reverseRequirements = Requirements.NONE, cost = 8)

    val ARDOUGNE_RANGE_HOUSE = CrossingSpec(ProductionAccessCrossing.ID,
        setOf(ArdougneHouseDoorAction.Lock.RANGE_HOUSE.door),
        Requirements(flags = setOf(ProductionAccessCrossing.RANGE_ADMISSION)),
        reverseRequirements = Requirements.NONE, cost = 8)

    val ARDOUGNE_CHEST_HOUSE_SOUTH = CrossingSpec(ProductionAccessCrossing.ID,
        setOf(ArdougneHouseDoorAction.Lock.CHEST_HOUSE_SOUTH.door),
        Requirements(flags = setOf(ProductionAccessCrossing.SOUTH_CHEST_ADMISSION)),
        reverseRequirements = Requirements.NONE, cost = 8)

    val ARDOUGNE_CASTLE = CrossingSpec(ProductionAccessCrossing.ID,
        setOf(ArdougneHouseDoorAction.Lock.CASTLE.door),
        Requirements(flags = setOf(ProductionAccessCrossing.CASTLE_ADMISSION)),
        reverseRequirements = Requirements.NONE, cost = 8)

    val WILDERNESS_HUT = CrossingSpec(ProductionAccessCrossing.ID,
        setOf(ArdougneHouseDoorAction.Lock.WILDERNESS_HUT.door),
        Requirements(items = listOf(ItemRequirement(1523)), flags = setOf(ProductionAccessCrossing.HUT_ADMISSION)),
        reverseRequirements = Requirements.NONE, cost = 8)

    val ALL = listOf(SHILO_FURNACE, ARDOUGNE_CHEST_HOUSE, ARDOUGNE_CHEST_HOUSE_SOUTH, ARDOUGNE_RANGE_HOUSE,
        ARDOUGNE_CASTLE, WILDERNESS_HUT)
}
