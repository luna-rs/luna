package api.bot.webwalk.generate

import api.bot.webwalk.walk.crossing.HangarExitCrossing
import game.obj.entrances.HangarExit

/**
 * The powered military hangar's native device provides a directed escape to the Stronghold.
 * Its ordinary first-option interaction is shared with players; the return remains the Daero journey.
 *
 * @author lare96
 */
object HangarExitCrossingSpec {

    val POWERED_HANGAR = CrossingSpec(HangarExitCrossing.ID, setOf(HangarExit.POWERED_DEVICE),
        bidirectional = false, cost = 8,
        placements = listOf(CrossingPlacement(HangarExit.POWERED_DEVICE, HangarExit.POWERED_POSITION,
            HangarExit.POWERED_APPROACH, HangarExit.DESTINATION)))
}
