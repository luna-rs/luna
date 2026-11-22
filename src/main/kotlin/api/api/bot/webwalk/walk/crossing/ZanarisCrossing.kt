package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.obj.entrances.Zanaris
import kotlin.time.Duration.Companion.seconds

/**
 * Uses the native Zanaris entrance and return ring.
 *
 * @author lare96
 */
object ZanarisCrossing : CrossingHandler {

    const val ID = "zanaris"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        if (context.arrived(leg, 0)) return null
        val action = leg.action ?: return "the Zanaris crossing has no object"
        val id = action.objectId ?: return "the Zanaris crossing has no object id"
        if (id != Zanaris.DOOR && id != Zanaris.RETURN_RING) return "unsupported Zanaris crossing $id"
        context.walk(leg.from, 0)?.let { return it }
        return use(context, leg)
    }

    /**
     * Clicks the actual entrance or return object and waits for its native action to finish.
     */
    private suspend fun use(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the Zanaris crossing has no object"
        val position = action.objectPosition ?: return "the Zanaris object has no position"
        val id = action.objectId ?: return "the Zanaris crossing has no object id"
        if (id == Zanaris.DOOR && !waitFor(10.seconds) { context.find(position, id) != null }) {
            return "the swamp shed door did not close"
        }
        val obj = context.find(position, id) ?: return "the Zanaris object $id is missing"
        if (!context.handler.interactions.interactOnLeg(1, obj) && !context.arrived(leg, 0)) {
            return "could not reach the Zanaris object $id"
        }
        return if (context.awaitArrival(leg, 0, 20.seconds)) null else "the Zanaris crossing did not reach its destination"
    }
}
