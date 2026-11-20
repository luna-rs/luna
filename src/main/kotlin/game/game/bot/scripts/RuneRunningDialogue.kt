package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.delay
import api.bot.social.BotConversations
import api.predef.rand
import engine.bot.coordinator.RuneRunningCoordinator
import engine.bot.speech.BotRuneRunningSpeechPool
import engine.bot.speech.BotRuneRunningSpeechPool.Event
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Pairs the participants for short, optional remarks about the exact service terms and result.
 *
 * @author lare96
 */
class RuneRunningDialogue(private val delivery: RuneRunningCoordinator.Delivery) {

    private companion object {
        private var Bot.nextRuneRunningRemark by Attr.long()
    }

    private enum class Phase { BEFORE, TRADE, AFTER, CLOSED }

    private var phase = Phase.BEFORE
    private val arrivals = mutableSetOf<Bot>()
    private val outcomes = mutableSetOf<Bot>()
    private var firstVisit = false
    private var selected = false
    private var conversation: BotConversations.Conversation? = null
    private var lines: List<String>? = null

    fun canSocialize(bot: Bot): Boolean = (bot === delivery.runner || bot === delivery.crafter) &&
        together() && when (phase) {
            Phase.BEFORE -> arrivals.size == 2
            Phase.AFTER -> outcomes.size == 2
            else -> false
        }

    suspend fun beforeTrade(bot: Bot, firstVisit: Boolean = false): Boolean {
        if (phase == Phase.CLOSED) return false
        arrivals += bot
        if (bot === delivery.runner) this.firstVisit = firstVisit
        withTimeoutOrNull(18.seconds) {
            while (phase == Phase.BEFORE && RuneRunningCoordinator.valid(delivery)) {
                if (arrivals.size == 2) {
                    if (!selected) select(if (this@RuneRunningDialogue.firstVisit) Event.INTRODUCTION
                        else Event.DELIVERY.takeIf { remarkDue() && rand(0.4) })
                    if (speak()) phase = Phase.TRADE
                }
                if (phase == Phase.BEFORE) delay(500.milliseconds)
            }
        }
        if (phase == Phase.BEFORE && arrivals.size == 2) {
            conversation?.close()
            phase = Phase.TRADE
        }
        return phase == Phase.TRADE && RuneRunningCoordinator.valid(delivery)
    }

    suspend fun afterTrade(bot: Bot) {
        if (phase == Phase.CLOSED || phase == Phase.BEFORE) return
        outcomes += bot
        if (phase == Phase.TRADE) {
            phase = Phase.AFTER
            selected = false
            lines = null
            conversation = null
        }
        withTimeoutOrNull(10.seconds) {
            while (phase == Phase.AFTER && together()) {
                if (outcomes.size == 2) {
                    if (!selected) select(if (delivery.paid) Event.THANKS.takeIf { rand(0.45) }
                        else Event.INTERRUPTED.takeIf { rand(0.45) })
                    if (speak()) close()
                }
                if (phase == Phase.AFTER) delay(500.milliseconds)
            }
        }
        close()
    }

    private fun together(): Boolean = RuneRunningCoordinator.atMeeting(delivery.runner, delivery.route) &&
        RuneRunningCoordinator.atMeeting(delivery.crafter, delivery.route)

    private fun remarkDue(): Boolean = System.currentTimeMillis() >=
        maxOf(delivery.runner.nextRuneRunningRemark, delivery.crafter.nextRuneRunningRemark)

    private fun select(event: Event?) {
        selected = true
        lines = event?.let {
            BotRuneRunningSpeechPool.select(delivery.runner, delivery.crafter, it, delivery.route.altar, delivery.fee)
        }
    }

    private fun speak(): Boolean {
        val remarks = lines ?: return true
        if (delivery.runner.speechStack.isAllDisabled || delivery.crafter.speechStack.isAllDisabled ||
            delivery.runner.ignores.contains(delivery.crafter.usernameHash) ||
            delivery.crafter.ignores.contains(delivery.runner.usernameHash)) return true

        val active = conversation ?: BotConversations.start(delivery.runner, delivery.crafter, remarks)
            ?.also { conversation = it } ?: return false
        active.tick()
        if (active.completed) {
            val next = System.currentTimeMillis() + 90_000
            delivery.runner.nextRuneRunningRemark = next
            delivery.crafter.nextRuneRunningRemark = next
        }
        return active.ended
    }

    fun close() {
        conversation?.close()
        phase = Phase.CLOSED
    }
}
