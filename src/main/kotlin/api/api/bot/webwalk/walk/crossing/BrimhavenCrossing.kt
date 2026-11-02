package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.obj.entrances.BrimhavenDungeonAccess
import io.luna.game.model.EntityState
import io.luna.game.model.mob.dialogue.DialogueInterface
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Pays the actual Saniboch NPC when needed and uses the dungeon's native entry, vines and agility interactions.
 * Exact landings distinguish successful crossings from lava falls. Retrying a failed obstacle never supplies tools,
 * edits admission credit, awards XP or substitutes a teleport for the native action.
 *
 * @author lare96
 */
object BrimhavenCrossing : CrossingHandler {

    const val ID = "brimhaven_dungeon"
    const val PREPAID = "brimhaven_prepaid"

    /**
     * A current-ability flag for a source direction that actually requires a skill or carried axe.
     */
    fun flag(passage: BrimhavenDungeonAccess.Passage): String = "brimhaven_${passage.key}"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the Brimhaven crossing has no action"
        val id = action.objectId
        val position = action.objectPosition ?: return "the Brimhaven crossing has no object position"
        if (context.arrived(leg, 0)) return null
        val entry = id == BrimhavenDungeonAccess.ENTRANCE && position == BrimhavenDungeonAccess.ENTRANCE_POSITION ||
            id == null && action.npcId == BrimhavenDungeonAccess.SANIBOCH
        if (entry &&
            leg.from == BrimhavenDungeonAccess.OUTSIDE && leg.to == BrimhavenDungeonAccess.INSIDE) {
            return enter(context, leg)
        }
        if (id == null) return "unsupported Brimhaven NPC direction"
        val exit = id == BrimhavenDungeonAccess.EXIT && position == BrimhavenDungeonAccess.EXIT_POSITION &&
            leg.from == BrimhavenDungeonAccess.INSIDE && leg.to == BrimhavenDungeonAccess.OUTSIDE
        val passage = BrimhavenDungeonAccess.passages.firstOrNull {
            it.id == id && it.position == position && it.from == leg.from && it.to == leg.to
        }
        if (!exit && passage == null) return "unknown Brimhaven object direction"
        repeat(3) {
            if (!ready(context)) return "the Brimhaven crossing was interrupted"
            if (passage != null && !BrimhavenDungeonAccess.canTraverse(context.bot, passage))
                return "the native Brimhaven direction needs its current skill level or usable carried axe"
            context.walk(leg.from, 0)?.let { return it }
            if (!waitFor(10.seconds) { context.find(position, id) != null }) return "the Brimhaven obstacle is unavailable"
            val obj = context.find(position, id) ?: return@repeat
            if (!context.handler.interactions.interactOnLeg(1, obj)) return "could not reach the Brimhaven obstacle"
            if (context.awaitArrival(leg, 0, 40.seconds)) return null
        }
        return "the native Brimhaven obstacle did not reach its successful landing"
    }

    private fun ready(context: LegContext): Boolean = !context.interrupted &&
        context.bot.state == EntityState.ACTIVE && context.bot.isAlive

    /**
     * Pays through Saniboch's second native option and explicitly continues its player line, coin display and reply.
     * Existing unused credit is retained across interruptions; only the native entrance consumes it.
     */
    private suspend fun enter(context: LegContext, leg: PlanLeg): String? {
        if (!ready(context)) return "the Brimhaven entry was interrupted"
        context.walk(leg.from, 0)?.let { return it }
        if (!BrimhavenDungeonAccess.hasPaid(context.bot)) {
            if (context.bot.inventory.computeAmountForId(BrimhavenDungeonAccess.COINS) < BrimhavenDungeonAccess.FEE)
                return "Saniboch requires 875 carried coins"
            val npc = context.findNpc(BrimhavenDungeonAccess.OUTSIDE, setOf(BrimhavenDungeonAccess.SANIBOCH), 12)
                ?: return "Saniboch is not at the native dungeon entrance"
            if (!context.handler.interactions.interactOnLeg(2, npc)) return "could not reach Saniboch to pay"
            val paid = withTimeoutOrNull(20.seconds) {
                while (ready(context)) {
                    val dialogue = context.bot.overlays.getOverlay(DialogueInterface::class.java)
                    if (dialogue != null) {
                        context.bot.output.sendContinueDialogue()
                    } else if (BrimhavenDungeonAccess.hasPaid(context.bot)) return@withTimeoutOrNull true
                    delay(500)
                }
                false
            } == true
            if (!paid) return "Saniboch did not grant native admission credit"
        }
        if (!ready(context) || !BrimhavenDungeonAccess.hasPaid(context.bot)) return "Brimhaven admission was interrupted"
        context.walk(leg.from, 0)?.let { return it }
        if (!waitFor(10.seconds) { context.find(BrimhavenDungeonAccess.ENTRANCE_POSITION,
                BrimhavenDungeonAccess.ENTRANCE) != null }) return "the dungeon entrance did not close"
        val entrance = context.find(BrimhavenDungeonAccess.ENTRANCE_POSITION, BrimhavenDungeonAccess.ENTRANCE)
            ?: return "the native Brimhaven entrance is missing"
        if (!context.handler.interactions.interactOnLeg(1, entrance)) return "could not use the paid Brimhaven entrance"
        return if (context.awaitArrival(leg, 0, 15.seconds)) null else "the Brimhaven entrance did not reach its native landing"
    }
}
