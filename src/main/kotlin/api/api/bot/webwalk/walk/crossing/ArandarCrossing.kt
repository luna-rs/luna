package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.obj.doors.ArandarGate
import kotlin.time.Duration.Companion.seconds

/**
 * Opens the Arandar palisade through its native interaction and waits for the crossing to finish.
 *
 * @author lare96
 */
object ArandarCrossing : CrossingHandler {

    const val ID = "arandar"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the palisade crossing has no action"
        val id = action.objectId ?: return "the palisade crossing has no object id"
        if (id != ArandarGate.LEFT && id != ArandarGate.RIGHT) return "unsupported Arandar gate $id"
        val position = action.objectPosition ?: return "the palisade crossing has no position"
        repeat(3) {
            if (context.arrived(leg, 0)) return null
            context.walk(leg.from, 0)?.let { return it }
            if (!waitFor(10.seconds) { context.find(position, id) != null }) return "the palisade did not close"
            val gate = context.find(position, id) ?: return@repeat
            if (!context.handler.interactions.interactOnLeg(1, gate)) return "could not reach the Arandar gate"
            if (context.awaitArrival(leg, 0)) return null
        }
        return "could not cross the Arandar palisade"
    }
}
