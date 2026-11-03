package api.bot.webwalk.generate

import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.TrollClimbingCrossing
import api.bot.webwalk.walk.crossing.TrollSlopeCrossing
import game.skill.agility.TrollSlopeRocks

/**
 * The two pairs of native top/bottom rock slopes that complete the walking path to Dad's arena.
 * Each side clicks its own source object and requires current Agility 15.
 *
 * @author lare96
 */
object TrollSlopeCrossingSpec {
    val SPEC = CrossingSpec(TrollSlopeCrossing.ID, setOf(TrollSlopeRocks.TOP, TrollSlopeRocks.BOTTOM),
        requirements = Requirements(flags = setOf(TrollClimbingCrossing.READY)), bidirectional = false, cost = 6,
        placements = TrollSlopeRocks.PASSAGES.map { CrossingPlacement(it.id, it.position, it.from, it.to) })
}
