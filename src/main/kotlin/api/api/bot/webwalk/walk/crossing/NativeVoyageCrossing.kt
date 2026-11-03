package api.bot.webwalk.walk.crossing

import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.content.nativeTravel.NativeVoyages
import io.luna.game.model.EntityState
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.mob.dialogue.DialogueQueue
import io.luna.game.model.mob.dialogue.NpcDialogue
import io.luna.game.model.mob.dialogue.OptionDialogue
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Takes the shore, island and Ape Atoll crew journeys through their ordinary Talk-to conversations.
 * Only the native confirmation option is selected; quick travel and direct movement are never used.
 *
 * @author lare96
 */
object NativeVoyageCrossing : CrossingHandler {

    const val ID = "native_voyage"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? =
        NativeDialogueControl.own(context.bot) { crossOwned(context, leg) }

    private suspend fun crossOwned(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the voyage leg has no NPC action"
        val route = NativeVoyages.Route.entries.firstOrNull { it.npcId == action.npcId && it.destination == leg.to }
            ?: return "unsupported native voyage to ${leg.to}"
        if (context.arrived(leg, 0)) return null
        context.walk(leg.from, 0)?.let { return it }
        val npc = context.findNpc(route.departure, setOf(route.npcId), 10)
            ?.takeIf { NativeVoyages.route(it) == route } ?: return "the voyage crew is not at ${route.departure}"
        if (!context.handler.interactions.interactOnLeg(1, npc)) return "could not talk to the voyage crew"
        val bot = context.bot
        var ownedPage: DialogueInterface? = null
        var ownedQueue: DialogueQueue? = null
        var continuedPage: NpcDialogue? = null
        var arrived = false
        try {
            arrived = withTimeoutOrNull(25_000) {
                var agreed = false
                repeat(45) {
                    if (context.arrived(leg, 0) && !bot.isLocked) return@withTimeoutOrNull true
                    if (bot.state != EntityState.ACTIVE || !bot.isAlive) return@withTimeoutOrNull false
                    if (!agreed && !bot.isLocked) {
                        if (npc.state != EntityState.ACTIVE || !bot.position.isWithinDistance(npc.position, 3) ||
                            ownedQueue != null && bot.dialogues !== ownedQueue) {
                            return@withTimeoutOrNull false
                        }
                        val options = bot.overlays.getOverlay(OptionDialogue::class.java)
                        if (options != null) {
                            if (continuedPage == null || ownedQueue == null || bot.dialogues !== ownedQueue ||
                                options.options != listOf(NativeVoyages.AGREE, NativeVoyages.DECLINE)) {
                                return@withTimeoutOrNull false
                            }
                            ownedPage = options
                            ownedQueue = bot.dialogues
                            if (!context.handler.widgets.clickDialogueOption(NativeVoyages.AGREE)) {
                                return@withTimeoutOrNull false
                            }
                            agreed = true
                        } else {
                            val question = bot.overlays.getOverlay(NpcDialogue::class.java)
                            if (question != null) {
                                if (question.npcId != route.npcId || question.text != listOf(route.question)) {
                                    return@withTimeoutOrNull false
                                }
                                ownedPage = question
                                ownedQueue = bot.dialogues
                                if (question !== continuedPage) {
                                    if (!bot.output.sendContinueDialogue()) return@withTimeoutOrNull false
                                    continuedPage = question
                                }
                            }
                        }
                    }
                    delay(500)
                }
                false
            } == true
        } finally {
            if (!arrived && !bot.isLocked) {
                val current = bot.overlays.getOverlay(DialogueInterface::class.java)
                val sameQueue = ownedQueue != null && bot.dialogues === ownedQueue
                val ownOptions = current is OptionDialogue &&
                    current.options == listOf(NativeVoyages.AGREE, NativeVoyages.DECLINE)
                if (current != null && (current === ownedPage || sameQueue && ownOptions)) {
                    // Clear synchronously: an already queued Agree must not outlive a failed/cancelled leg.
                    bot.overlays.closeWindows()
                }
            }
        }
        return if (arrived) null else "the native voyage did not finish at ${route.destination}"
    }
}
