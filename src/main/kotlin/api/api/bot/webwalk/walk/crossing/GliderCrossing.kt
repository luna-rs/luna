package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.generate.GliderRouteGenerator
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.content.gnomeGliders.GliderSite
import game.content.gnomeGliders.GnomeGliders.GliderMap
import io.luna.game.model.EntityState
import io.luna.game.model.mob.dialogue.OptionDialogue
import kotlin.time.Duration.Companion.seconds

/**
 * Requests a native pilot flight, selects its map destination and verifies the ordinary delayed landing.
 * The existing dialogue reflex advances speech; only the flight option and owned map are handled here.
 * Movement, animation, quest admission and map closure stay with GnomeGliders.
 *
 * @author lare96
 */
object GliderCrossing : CrossingHandler {
    const val ID = "gnome_glider"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        if (context.arrived(leg, 1)) return null
        val from = GliderSite.entries.firstOrNull { leg.from.isWithinDistance(it.landing, 1) }
            ?: return "unknown glider departure ${leg.from}"
        val to = GliderSite.entries.firstOrNull { leg.to.isWithinDistance(it.landing, 1) }
            ?: return "unknown glider arrival ${leg.to}"
        if (!GliderRouteGenerator.flies(from, to)) return "no native glider flight from $from to $to"
        context.walk(leg.from, 0)?.let { return it }
        val id = if (from == GliderSite.LEMANTOLLY_UNDRI) 1800 else 170
        val npc = context.findNpc(from.landing, setOf(id), 8) ?: return "no pilot at $from"
        val bot = context.bot
        if (bot.overlays.hasWindow() || !context.handler.interactions.interactOnLeg(1, npc))
            return "could not request a flight from $from"
        if (!waitFor(10.seconds) { bot.overlays.getOverlay(OptionDialogue::class.java) != null })
            return "the pilot did not offer a flight"
        val option = if (from == GliderSite.LEMANTOLLY_UNDRI) "Can I take a flight in the glider?"
            else "Can you take me on the glider?"
        if (!context.handler.widgets.clickDialogueOption(option)) return "the flight option was unavailable"
        if (!waitFor(10.seconds) { bot.overlays.getOverlay(GliderMap::class.java)?.from == from })
            return "the pilot did not open the glider map"
        val map = bot.overlays.getOverlay(GliderMap::class.java) ?: return "the glider map closed"
        if (bot.state != EntityState.ACTIVE || bot.overlays.getOverlay(GliderMap::class.java) !== map ||
            !bot.output.clickButton(to.buttons.first())) return "the glider destination could not be selected"
        return if (context.awaitArrival(leg, 2, 10.seconds) && waitFor(5.seconds) { !bot.isLocked }) null
            else "the native glider did not finish its flight to $to"
    }
}
