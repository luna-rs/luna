package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import api.predef.ext.*
import game.obj.entrances.ShiloVillageTravel
import io.luna.game.model.mob.dialogue.OptionDialogue

/**
 * Boards the Brimhaven or Shilo cart and agrees to the driver's quoted native fare.
 *
 * @author lare96
 */
object ShiloVillageTravelCrossing : CrossingHandler {

    const val ID = "shilo_cart_travel"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        if (context.arrived(leg, 0)) return null
        val action = leg.action ?: return "the cart journey has no action"
        val id = action.objectId ?: return "the cart journey has no object id"
        if (id != ShiloVillageTravel.BRIMHAVEN_CART && id != ShiloVillageTravel.SHILO_CART) {
            return "unsupported travel cart $id"
        }
        val position = action.objectPosition ?: return "the travel cart has no position"
        context.walk(leg.from, 0)?.let { return it }
        val cart = context.find(position, id) ?: return "the travel cart is missing"
        if (!context.handler.interactions.interactOnLeg(2, cart)) return "could not reach the travel cart"
        if (!waitFor(LegContext.ACTION_TIMEOUT) { OptionDialogue::class in context.bot.overlays }) {
            return "the cart driver did not offer a journey"
        }
        if (!context.handler.widgets.clickDialogueOption(1)) return "could not agree to the cart fare"
        return if (context.awaitArrival(leg, 0)) null else "the cart journey did not reach ${leg.to}"
    }
}
