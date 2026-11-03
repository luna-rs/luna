package api.bot.webwalk.generate

import api.bot.webwalk.walk.crossing.KaramjaLogCrossing
import game.skill.agility.KaramjaLog

/**
 * Both raw plane-one Karamja log ends, clicked from their rendered ground-plane riverbanks with no level requirement.
 *
 * @author lare96
 */
object KaramjaLogCrossingSpec {
    val SPEC = CrossingSpec(KaramjaLogCrossing.ID, setOf(KaramjaLog.ID), bidirectional = false, cost = 6,
        placements = KaramjaLog.PASSAGES.map { CrossingPlacement(KaramjaLog.ID, it.position, it.from, it.to) })
}
