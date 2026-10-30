package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.content.waterbirth.Jarvald
import io.luna.game.model.mob.dialogue.OptionDialogue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import io.luna.game.model.EntityState

/**
 * Boards Jarvald through his native conversation and verifies the shore landing.
 *
 * @author lare96
 */
object JarvaldCrossing : CrossingHandler {
    const val ID = "jarvald"
    const val PAYMENT = "jarvald_fare"
    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val route = Jarvald.Route.entries.firstOrNull { leg.to.isWithinDistance(it.destination, 3) }
            ?: return "unknown Jarvald destination ${leg.to}"
        if (context.arrived(leg, 1)) return null
        if (Jarvald.fare(context.bot) > context.bot.inventory.computeAmountForId(995)) return "not enough coins for Jarvald"
        context.walk(leg.from, 0)?.let { return it }
        val npc = context.findNpc(route.departure, route.crew, 8) ?: return "Jarvald is not at ${route.departure}"
        if (!context.handler.interactions.interactOnLeg(1, npc)) return "could not reach Jarvald"
        val ready = withTimeoutOrNull(10_000) {
            repeat(12) {
                if (context.bot.overlays.getOverlay(OptionDialogue::class.java) != null) return@withTimeoutOrNull true
                if (!context.bot.isAlive || context.bot.state != EntityState.ACTIVE ||
                    !context.bot.position.isWithinDistance(npc.position, 3)) return@withTimeoutOrNull false
                context.bot.output.sendContinueDialogue()
                delay(500)
            }
            false
        } == true
        if (!ready)
            return "Jarvald did not offer a voyage"
        if (!context.handler.widgets.clickDialogueOption("Yes, let's go.")) return "Jarvald's voyage option was unavailable"
        return if (context.awaitArrival(leg, 1, 20.seconds)) null else "Jarvald did not land at ${leg.to}"
    }
}
