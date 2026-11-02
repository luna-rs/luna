package api.bot.webwalk.generate

import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.ShiloVillageTravelCrossing
import game.obj.entrances.ShiloVillageTravel
import io.luna.game.model.Position

/**
 * The two native cart journeys connecting Shilo Village with Brimhaven.
 *
 * @author lare96
 */
object ShiloVillageCrossingSpec {

    /**
     * Reserves the maximum native fare so later legs retain their planned funds. The driver charges the quoted amount.
     */
    val TRAVEL = CrossingSpec(ShiloVillageTravelCrossing.ID,
        setOf(ShiloVillageTravel.BRIMHAVEN_CART, ShiloVillageTravel.SHILO_CART),
        Requirements(coins = ShiloVillageTravel.MAX_FARE), bidirectional = false, cost = 6,
        placements = listOf(
            CrossingPlacement(ShiloVillageTravel.BRIMHAVEN_CART, Position(2777, 3211),
                ShiloVillageTravel.BRIMHAVEN, ShiloVillageTravel.SHILO),
            CrossingPlacement(ShiloVillageTravel.SHILO_CART, Position(2831, 2952),
                ShiloVillageTravel.SHILO, ShiloVillageTravel.BRIMHAVEN)))
}
