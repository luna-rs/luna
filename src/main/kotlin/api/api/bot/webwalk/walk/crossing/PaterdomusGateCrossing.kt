package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.obj.entrances.PaterdomusGates
import kotlin.time.Duration.Companion.seconds

/**
 * Opens each Paterdomus basement gate through its native passage action, waiting for any other traveler
 * to finish before retrying. The crossing never changes the player's position or quest progress directly.
 *
 * @author lare96
 */
object PaterdomusGateCrossing : CrossingHandler {
    const val ID = "paterdomus_gate"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the Paterdomus leg has no gate"
        val id = action.objectId ?: return "the Paterdomus gate has no id"
        if (id !in PaterdomusGates.IDS) return "unsupported Paterdomus gate $id"
        val position = action.objectPosition ?: return "the Paterdomus gate has no position"
        repeat(3) {
            if (context.arrived(leg, 0)) return null
            context.walk(leg.from, 0)?.let { return it }
            if (!waitFor(10.seconds) { context.find(position, id) != null })
                return "Paterdomus gate $id did not close"
            val gate = context.find(position, id) ?: return@repeat
            if (!context.handler.interactions.interactOnLeg(1, gate)) return "could not reach Paterdomus gate $id"
            if (context.awaitArrival(leg, 0, 4.seconds)) return null
        }
        return "could not pass Paterdomus gate $id"
    }
}
