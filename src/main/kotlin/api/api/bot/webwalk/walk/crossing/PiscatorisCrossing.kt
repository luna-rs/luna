package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import api.predef.ext.*
import game.content.piscatoris.PiscatorisFerry
import io.luna.game.model.EntityState
import io.luna.game.model.mob.dialogue.OptionDialogue
import kotlin.time.Duration.Companion.seconds

/**
 * Boards Kathy's boat through the native conversation and verifies the opposite-shore landing.
 *
 * @author lare96
 */
object PiscatorisCrossing : CrossingHandler {

    const val ID = "piscatoris_ferry"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val route = PiscatorisFerry.Route.entries.firstOrNull { leg.to == it.destination }
            ?: return "unknown Piscatoris ferry destination ${leg.to}"
        if (context.arrived(leg, 0)) return null
        val bot = context.bot
        if (bot.inventory.computeAmountForId(995) < PiscatorisFerry.FARE) return "not enough coins for Kathy's boat"
        context.walk(leg.from, 0)?.let { return it }
        val npc = context.findNpc(route.departure, PiscatorisFerry.CREW, 10)
            ?.takeIf { PiscatorisFerry.route(it) == route } ?: return "Kathy is not at the departure shore"
        if (!context.handler.interactions.interactOnLeg(1, npc)) return "could not reach Kathy"
        if (!waitFor(15.seconds) {
                OptionDialogue::class in bot.overlays || bot.state != EntityState.ACTIVE || !bot.isAlive
            }) return "Kathy did not offer the river journey"
        val options = bot.overlays[OptionDialogue::class]
        if (options == null || PiscatorisFerry.AGREE !in options.options || bot.state != EntityState.ACTIVE || !bot.isAlive ||
            !context.handler.widgets.clickDialogueOption(PiscatorisFerry.AGREE)) {
            return "could not agree to Kathy's boat fare"
        }
        return if (context.awaitArrival(leg, 0, 20.seconds)) null else "Kathy's boat did not reach the opposite shore"
    }
}
