package api.bot.webwalk.walk.crossing

import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.item.greegree.Greegree
import game.obj.doors.BambooGate
import io.luna.game.model.EntityState
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Equips an already carried greegree on Ape Atoll and uses the ravine gate's ordinary first-option passage.
 *
 * @author lare96
 */
object ApeGateCrossing : CrossingHandler {
    const val ID = "ape_gate"
    const val GREEGREE_READY = "ape_gate_greegree_ready"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? =
        NativeDialogueControl.own(context.bot) { crossOwned(context, leg) }

    private suspend fun crossOwned(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the Ape Atoll gate leg has no object action"
        val passage = BambooGate.PASSAGES.firstOrNull {
            action.objectId == it.id && action.objectPosition == it.position && leg.from == it.from && leg.to == it.to
        } ?: return "unsupported Ape Atoll gate passage"
        if (context.arrived(leg, 0)) return null
        val bot = context.bot
        if (!Greegree.worksAt(bot.position)) return "a greegree cannot disguise the bot here"
        if (!Greegree.disguises(bot)) {
            val greegree = Greegree.ITEMS.keys.firstOrNull { bot.inventory.contains(it) }
                ?: return "the Ape Atoll gate requires a carried or worn monkey greegree"
            if (!context.handler.equipment.equip(greegree) || !Greegree.disguises(bot)) {
                return "could not equip the carried monkey greegree"
            }
        }
        context.walk(leg.from, 0)?.let { return it }
        val restored = withTimeoutOrNull(10_000) {
            while (BambooGate.isBusy(bot.world)) {
                if (context.interrupted || bot.state != EntityState.ACTIVE || !bot.isAlive ||
                    bot.position != leg.from || !Greegree.disguises(bot)) return@withTimeoutOrNull false
                delay(300)
            }
            true
        } == true
        if (!restored) return "the Ape Atoll gate's previous passage did not finish"
        if (context.interrupted || bot.position != leg.from || !Greegree.disguises(bot)) {
            return "the monkey disguise or gate approach changed"
        }
        val obj = context.find(passage.position, passage.id)
            ?: return "the closed Ape Atoll gate is missing"
        if (!context.handler.interactions.interactOnLeg(1, obj)) return "could not open the Ape Atoll gate"
        return if (context.awaitArrival(leg, 0, 10.seconds)) null else "the Ape Atoll gate passage did not finish"
    }
}
