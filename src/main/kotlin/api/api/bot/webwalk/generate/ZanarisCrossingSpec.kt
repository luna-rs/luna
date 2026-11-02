package api.bot.webwalk.generate

import api.bot.webwalk.model.ItemRequirement
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.ZanarisCrossing
import game.obj.entrances.Zanaris
import io.luna.game.model.Position

/**
 * The staff-equipped shed entrance and the separate, staff-free return ring.
 *
 * @author lare96
 */
object ZanarisCrossingSpec {

    val ENTRY = CrossingSpec(ZanarisCrossing.ID, setOf(Zanaris.DOOR),
        Requirements(items = listOf(ItemRequirement(Zanaris.STAFF))), bidirectional = false, cost = 8,
        placements = listOf(CrossingPlacement(Zanaris.DOOR, Position(3202, 3169),
            Zanaris.SHED_OUTSIDE, Zanaris.LANDING)))

    val EXIT = CrossingSpec(ZanarisCrossing.ID, setOf(Zanaris.RETURN_RING), bidirectional = false, cost = 3,
        placements = listOf(CrossingPlacement(Zanaris.RETURN_RING, Position(2452, 4473),
            Zanaris.RETURN_APPROACH, Zanaris.SHED_OUTSIDE)))
}
