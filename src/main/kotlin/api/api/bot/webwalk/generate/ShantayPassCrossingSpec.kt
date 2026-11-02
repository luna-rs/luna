package api.bot.webwalk.generate

import api.bot.webwalk.model.ItemRequirement
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.ShantayPassCrossing
import game.npc.spawn.shantayPass.ShantayPass

/**
 * The actual desert gateway, with a ticket required only on the journey into the desert.
 *
 * @author lare96
 */
object ShantayPassCrossingSpec {
    val SPEC = CrossingSpec(ShantayPassCrossing.ID, setOf(ShantayPass.DOORWAY),
        Requirements(items = listOf(ItemRequirement(ShantayPass.PASS))), reverseRequirements = Requirements.NONE,
        cost = 12, placements = listOf(CrossingPlacement(ShantayPass.DOORWAY, ShantayPass.POSITION,
            ShantayPass.NORTH, ShantayPass.SOUTH)))
}
