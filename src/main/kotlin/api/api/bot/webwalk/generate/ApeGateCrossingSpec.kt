package api.bot.webwalk.generate

import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.ApeGateCrossing
import game.obj.doors.BambooGate

/**
 * The ravine gate's asymmetric three-tile passages require an owned greegree in either direction.
 *
 * @author lare96
 */
object ApeGateCrossingSpec {
    val GATE = CrossingSpec(ApeGateCrossing.ID, BambooGate.IDS,
        requirements = Requirements(flags = setOf(ApeGateCrossing.GREEGREE_READY)), bidirectional = false, cost = 5,
        placements = BambooGate.PASSAGES.map { CrossingPlacement(it.id, it.position, it.from, it.to) })
}
