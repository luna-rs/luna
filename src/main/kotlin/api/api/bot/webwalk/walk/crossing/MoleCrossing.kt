package api.bot.webwalk.walk.crossing

import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.item.teleport.SpadeDigSpot
import game.skill.firemaking.lightSource.LightSource
import io.luna.game.model.mob.Player
import kotlin.time.Duration.Companion.seconds

/**
 * Activates a real carried spade at a legal native Mole hill. Existing spade and darkness handlers alone
 * perform the fall, so losing the light/tool or another native dig result cannot fabricate lair admission.
 *
 * @author lare96
 */
object MoleCrossing : CrossingHandler {
    const val ID = "mole_spade"
    const val SPADE = 952
    const val LIGHT_READY = "mole_light_ready"

    /**
     * Matches native darkness admission; a carried or worn light must actually emit brightness.
     */
    fun lightReady(player: Player): Boolean = LightSource.brightness(player) > 0

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        if (context.arrived(leg, 0)) return null
        val action = leg.action ?: return "the Mole entry has no item action"
        val spot = SpadeDigSpot.FALADOR_MOLE_HILLS
        if (action.itemId != SPADE || leg.to != spot.landing || SpadeDigSpot.forPosition(leg.from) != spot)
            return "the crossing is not a legal native Mole dig"
        context.walk(leg.from, 0)?.let { return it }
        val bot = context.bot
        if (!lightReady(bot) || SpadeDigSpot.forPosition(bot.position) != spot) return "native Mole digging needs a lit light"
        val slot = bot.inventory.computeIndexForId(SPADE)
        if (slot < 0) return "native Mole digging needs a carried spade"
        if (!context.handler.inventory.clickItem(1, SPADE, slot)) return "could not use the carried spade"
        return if (context.awaitArrival(leg, 0, 10.seconds)) null else "the native dig did not reach the Mole lair"
    }
}
