package api.bot.webwalk.walk.crossing

import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.skill.agility.TrollSlopeRocks
import io.luna.game.model.mob.Skill
import kotlin.time.Duration.Companion.seconds

/**
 * Uses native slope interactions and retries only failed ascents, which return to their original side.
 *
 * @author lare96
 */
object TrollSlopeCrossing : CrossingHandler {
    const val ID = "troll_slope_rocks"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the Troll slope has no object"
        val passage = TrollSlopeRocks.PASSAGES.firstOrNull {
            action.objectId == it.id && action.objectPosition == it.position && leg.from == it.from && leg.to == it.to
        } ?: return "unsupported Troll rock slope"
        repeat(5) {
            if (context.arrived(leg, 0)) return null
            context.walk(leg.from, 0)?.let { return it }
            if (context.bot.skill(Skill.AGILITY).level < TrollSlopeRocks.LEVEL)
                return "the Troll rock slope requires current Agility 15"
            val rock = context.find(passage.position, passage.id) ?: return "the Troll rock slope is missing"
            if (!context.handler.interactions.interactOnLeg(1, rock)) return "could not reach the Troll rock slope"
            if (context.awaitArrival(leg, 0, 5.seconds)) return null
        }
        return "fell back from the Troll rock slope after five attempts"
    }
}
