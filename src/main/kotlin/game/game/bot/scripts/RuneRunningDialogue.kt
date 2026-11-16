package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.delay
import api.bot.social.BotConversations
import api.predef.rand
import engine.bot.coordinator.RuneRunningCoordinator
import engine.bot.coordinator.RuneRunningCoordinator.BATCH
import engine.bot.coordinator.RuneRunningCoordinator.COINS
import engine.bot.coordinator.RuneRunningCoordinator.NATURE_FARE
import engine.bot.speech.BotRuneRunningSpeechPool
import engine.bot.speech.BotRuneRunningSpeechPool.Event
import game.skill.runecrafting.craftRune.CraftableRune
import game.skill.runecrafting.enterAltar.Altar
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Holds a delivery at its outside-ruins meeting point while both owners discuss the job and acknowledge its result.
 * Every turn uses the shared conversation runner and waits for native public-chat delivery before its reply.
 *
 * @author lare96
 */
class RuneRunningDialogue(private val delivery: RuneRunningCoordinator.Delivery) {

    /**
     * Brief repeat remarks share a cooldown across deliveries and replacement scripts.
     */
    private companion object {
        private var Bot.nextRuneRunningRemark by Attr.long()
    }

    private enum class Phase { BEFORE, TRADE, AFTER, CLOSED }
    private enum class Progress { WAITING, DELIVERED, SKIPPED, CANCELED }

    private var phase = Phase.BEFORE
    private val arrivals = mutableSetOf<Bot>()
    private val outcomes = mutableMapOf<Bot, Boolean>()
    private var firstVisit = false
    private var leaving = false
    private var selected = false
    private var event: Event? = null
    private var lines: List<String>? = null
    private var conversation: BotConversations.Conversation? = null

    /**
     * Only publicly delivered introductions suppress the next visit's introduction.
     */
    var introductionDelivered = false
        private set

    /**
     * The runner should bank and leave when the host cannot fund another load.
     */
    var hostFinished = false
        private set

    /**
     * Finite result dialogue may finish after the work deadline without starting another delivery.
     */
    val acknowledging: Boolean get() = phase == Phase.AFTER

    /**
     * Both task owners must have reached the same spoken phase before either becomes socially available.
     */
    fun canSocialize(bot: Bot): Boolean = participant(bot) && valid() && when (phase) {
        Phase.BEFORE -> arrivals.size == 2
        Phase.AFTER -> outcomes.size == 2
        else -> false
    }

    /**
     * First visits explain actual terms, starter visits explain the funded load, and return greetings are occasional.
     * External mutes and ignores skip speech without changing their settings or making the job unusable.
     */
    suspend fun beforeTrade(bot: Bot, firstVisit: Boolean = false): Boolean {
        if (!participant(bot) || phase == Phase.CLOSED) return false
        arrivals += bot
        if (bot === delivery.runner) this.firstVisit = firstVisit
        val ready = withTimeoutOrNull(65.seconds) {
            while (phase == Phase.BEFORE && valid()) {
                if (arrivals.size == 2) {
                    if (!selected) {
                        event = when {
                            delivery.seed -> Event.STARTER
                            this@RuneRunningDialogue.firstVisit -> Event.INTRODUCTION
                            remarkDue() && rand(0.35) -> Event.DELIVERY
                            else -> null
                        }
                        selectLines()
                    }
                    when (advance()) {
                        Progress.DELIVERED -> {
                            introductionDelivered = event == Event.INTRODUCTION || event == Event.STARTER
                            phase = Phase.TRADE
                        }
                        Progress.SKIPPED -> phase = Phase.TRADE
                        Progress.CANCELED -> close()
                        Progress.WAITING -> Unit
                    }
                }
                if (phase == Phase.BEFORE) delay(250.milliseconds)
            }
            phase == Phase.TRADE && valid()
        } ?: false
        if (!ready) close()
        return ready
    }

    /**
     * Acknowledgements wait for both independent native trade results. A canceled trade cannot produce thanks for pay.
     * Both owners remain here until the reply is delivered, so banking or crafting cannot interrupt it.
     */
    suspend fun afterTrade(bot: Bot, success: Boolean, leaving: Boolean = false) {
        if (!participant(bot) || phase == Phase.CLOSED) return
        outcomes[bot] = success
        this.leaving = this.leaving || leaving
        if (phase == Phase.TRADE) {
            phase = Phase.AFTER
            selected = false
            event = null
            lines = null
            conversation = null
        }
        withTimeoutOrNull(40.seconds) {
            while (phase == Phase.AFTER && valid()) {
                if (outcomes.size == 2) {
                    if (!selected) {
                        val paid = outcomes.values.all { it }
                        val note = api.predef.itemDef(delivery.essenceId).notedId.orElse(-1)
                        hostFinished = paid && (note < 0 || delivery.crafter.inventory.computeAmountForId(note) < BATCH ||
                            delivery.altar == Altar.NATURE &&
                            delivery.crafter.inventory.computeAmountForId(COINS) < NATURE_FARE)
                        event = when {
                            !paid -> Event.INTERRUPTED
                            delivery.seed -> Event.STARTER_RECEIVED
                            hostFinished -> Event.HOST_FINISHED
                            this@RuneRunningDialogue.leaving -> Event.FAREWELL
                            remarkDue() && rand(0.5) -> Event.THANKS
                            else -> null
                        }
                        selectLines()
                    }
                    if (advance() != Progress.WAITING) close()
                }
                if (phase == Phase.AFTER) delay(250.milliseconds)
            }
        }
        close()
    }

    private fun participant(bot: Bot) = bot === delivery.runner || bot === delivery.crafter

    private fun valid() = RuneRunningCoordinator.valid(delivery) &&
        delivery.runner.isWithinDistance(delivery.crafter, 8)

    private fun remarkDue() = System.currentTimeMillis() >= maxOf(
        delivery.runner.nextRuneRunningRemark, delivery.crafter.nextRuneRunningRemark)

    /**
     * The immutable contract supplies rune and fare quantities; a starter gift never advertises a rune wage as received.
     */
    private fun selectLines() {
        selected = true
        val kind = event ?: return
        val rune = CraftableRune.ALTAR_TO_RUNE.getValue(delivery.altar)
        val amount = if (delivery.seed) delivery.crafter.inventory.computeAmountForId(rune.id)
            else delivery.crafterGives.firstOrNull { it.id == rune.id }?.amount ?: 0
        val fare = delivery.crafterGives.firstOrNull { it.id == COINS }?.amount ?: 0
        lines = BotRuneRunningSpeechPool.select(delivery.runner, delivery.crafter, kind, delivery.altar, amount, fare)
    }

    /**
     * Shared mute, ignore, movement, overlay and script-ownership checks remain with the normal conversation system.
     */
    private fun advance(): Progress {
        if (lines == null || delivery.runner.speechStack.isAllDisabled || delivery.crafter.speechStack.isAllDisabled ||
            delivery.runner.ignores.contains(delivery.crafter.usernameHash) ||
            delivery.crafter.ignores.contains(delivery.runner.usernameHash)) {
            conversation?.close()
            return Progress.SKIPPED
        }
        if (conversation == null) {
            conversation = BotConversations.start(delivery.runner, delivery.crafter, lines!!) ?: return Progress.WAITING
        }
        val current = conversation!!
        current.tick()
        if (!current.ended) return Progress.WAITING
        if (!current.completed) return Progress.CANCELED
        val next = System.currentTimeMillis() + 90_000
        delivery.runner.nextRuneRunningRemark = next
        delivery.crafter.nextRuneRunningRemark = next
        return Progress.DELIVERED
    }

    /**
     * Releases only this delivery's paired speech, including pending turns that must not arrive after departure.
     */
    fun close() {
        conversation?.close()
        phase = Phase.CLOSED
    }
}
