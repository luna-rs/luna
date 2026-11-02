package api.bot.webwalk.walk.crossing

import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.skill.agility.TrollClimbingRocks
import kotlin.time.Duration.Companion.seconds

/**
 * Retries the real Troll rock action after slips, checking current level and worn boots at the obstacle.
 * Only the exact opposite stand completes the leg, so a failed climb can never count as a successful crossing.
 *
 * @author lare96
 */
object TrollClimbingCrossing : CrossingHandler {
    const val ID = "troll_climbing_rocks"
    const val READY = "troll_climbing_rocks_ready"
    const val BOOTS_WORN = "troll_climbing_boots_worn"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the Troll climb has no object"
        val position = action.objectPosition ?: return "the Troll rock has no position"
        if (action.objectId != TrollClimbingRocks.ROCK || TrollClimbingRocks.PASSAGES.none {
                it.position == position && (it.from == leg.from && it.to == leg.to || it.to == leg.from && it.from == leg.to)
            }) return "unsupported Troll climbing-rock passage"
        repeat(5) {
            if (context.arrived(leg, 0)) return null
            context.walk(leg.from, 0)?.let { return it }
            if (!TrollClimbingRocks.canTraverse(context.bot, leg.from))
                return "the Troll climb requires current Agility15 and worn climbing boots on its southern approach"
            val rock = context.find(position, TrollClimbingRocks.ROCK) ?: return "the Troll climbing rock is missing"
            if (!context.handler.interactions.interactOnLeg(1, rock)) return "could not reach the Troll climbing rock"
            if (context.awaitArrival(leg, 0, 4.seconds)) return null
        }
        return "slipped back from the Troll rocks after five attempts"
    }
}
