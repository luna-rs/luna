package api.bot.webwalk.generate

import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.TrollClimbingCrossing
import game.skill.agility.TrollClimbingRocks

/**
 * The source-placed ordinary Troll climbing rocks, with current Agility required in both directions
 * and worn boots required only at the southern approach. The native slip keeps the bot on its starting side.
 *
 * @author lare96
 */
object TrollClimbingCrossingSpec {
    private val level = Requirements(flags = setOf(TrollClimbingCrossing.READY))
    private val southernEntry = Requirements(flags = setOf(TrollClimbingCrossing.READY, TrollClimbingCrossing.BOOTS_WORN))

    val ALL = listOf(CrossingSpec(TrollClimbingCrossing.ID, setOf(TrollClimbingRocks.ROCK), level,
        reverseRequirements = level, cost = 5,
        placements = TrollClimbingRocks.PASSAGES.map { passage ->
            CrossingPlacement(TrollClimbingRocks.ROCK, passage.position, passage.from, passage.to,
                requirements = southernEntry.takeIf { passage.from.y == 3611 })
        }))
}
