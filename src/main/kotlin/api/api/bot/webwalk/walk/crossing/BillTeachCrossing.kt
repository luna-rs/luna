package api.bot.webwalk.walk.crossing

import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.content.mosLeHarmless.BillTeach
import io.luna.game.model.EntityState
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.mob.dialogue.DialogueQueue
import io.luna.game.model.mob.dialogue.NpcDialogue
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.dialogue.PlayerDialogue
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Takes Bill Teach's ordinary Talk-to voyage and waits until the bot can move on the other deck.
 *
 * @author lare96
 */
object BillTeachCrossing : CrossingHandler {

    const val ID = "bill_teach"
    const val ADMISSION = "bill_teach_sailing"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val port = BillTeach.Port.entries.firstOrNull { it.npcId == leg.action?.npcId && it.other.deck == leg.to }
            ?: return "the leg does not match a Bill Teach voyage"
        val bot = context.bot
        if (context.arrived(leg, 0) && !bot.isLocked) return null
        if (!BillTeach.canSail(bot)) return "the bot cannot sail with Bill Teach"
        context.walk(leg.from, 0)?.let { return it }
        val bill = context.findNpc(port.deck, setOf(port.npcId), 8) ?: return "Bill Teach is missing at $port"
        if (bot.overlays.hasWindow()) return "another window is open before talking to Bill Teach"

        val arrived = NativeDialogueControl.own(bot) {
            val previousQueue = bot.dialogues
            var ownedQueue: DialogueQueue? = null
            var continuedPage: DialogueInterface? = null
            var step = 0
            try {
                if (!context.handler.interactions.interactOnLeg(1, bill)) return@own false
                withTimeoutOrNull(18_000) {
                    while (bot.state == EntityState.ACTIVE && bot.isAlive) {
                        if (!bot.isLocked) {
                            if (context.arrived(leg, 0)) return@withTimeoutOrNull true
                            if (bill.state != EntityState.ACTIVE || !bot.position.isWithinDistance(bill.position, 3)) {
                                return@withTimeoutOrNull false
                            }
                            val page = bot.overlays.getOverlay(DialogueInterface::class.java)
                            val queue = bot.dialogues
                            if (page != null && page !== continuedPage) {
                                if (queue == null || queue === previousQueue || !queue.owns(page)) return@withTimeoutOrNull false
                                val matches = when (step) {
                                    0 -> page is NpcDialogue && page.npcId == port.npcId &&
                                        page.text.joinToString(" ") == BillTeach.question(bot)
                                    1 -> queue === ownedQueue && page is OptionDialogue &&
                                        page.options == listOf(BillTeach.ACCEPT, BillTeach.DECLINE)
                                    2 -> queue !== ownedQueue && confirmsLift(page)
                                    3 -> queue === ownedQueue && page is NpcDialogue && page.npcId == port.npcId &&
                                        page.text.joinToString(" ") == BillTeach.reply(bot, port)
                                    else -> false
                                }
                                if (!matches) return@withTimeoutOrNull false
                                ownedQueue = queue
                                continuedPage = page
                                val advanced = if (step == 1) context.handler.widgets.clickDialogueOption(BillTeach.ACCEPT)
                                    else bot.output.sendContinueDialogue()
                                if (!advanced) return@withTimeoutOrNull false
                                step++
                            }
                        }
                        delay(300)
                    }
                    false
                } == true
            } finally {
                val page = bot.overlays.getOverlay(DialogueInterface::class.java)
                val queue = bot.dialogues
                // Accept opens a fresh queue. It may already own the reply when the webwalk is cancelled.
                if (queue?.owns(page) == true && (queue === ownedQueue || step == 2 && confirmsLift(page))) {
                    bot.overlays.closeWindows()
                }
            }
        }
        return if (arrived) null else "Bill Teach did not finish the voyage from $port"
    }

    private fun confirmsLift(page: DialogueInterface?): Boolean =
        page is PlayerDialogue && page.lines.joinToString(" ") == BillTeach.ACCEPT_REPLY
}
