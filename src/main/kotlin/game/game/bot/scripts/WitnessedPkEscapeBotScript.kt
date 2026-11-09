package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.script.BotEscape
import api.bot.script.BotScript
import api.bot.script.DynamicBotScript
import api.bot.script.ZonedBotScript
import api.bot.script.lastEscape
import engine.controllers.Controllers.inWilderness
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Temporarily interrupts ordinary work to leave a witnessed Wilderness attack without joining the fight.
 * The interrupted activity resumes through its normal stack lifecycle after bounded native escape attempts.
 *
 * @property observedAt The observer's tile when it saw the attack, retained for temporary avoidance.
 * @property attacker The attacker's display name, used only in the escape record and log.
 * @property parent The exact activity whose cancellation cleanup must settle before escape takes ownership.
 * @author lare96
 */
class WitnessedPkEscapeBotScript(bot: Bot, private val observedAt: Position, private val attacker: String,
                                 private val parent: BotScript? = bot.scriptStack.current()) : DynamicBotScript(bot) {

    override val actionSafety = ActionSafety.COMBAT
    override val allowsCombatResponse = false

    private val deadline = System.currentTimeMillis() + 90.seconds.inWholeMilliseconds
    private var attempts = 0
    private var guardsInstalled = false
    private var previousCombatDisabled = false
    private var previousWandering = false
    private var previousRunning = false
    private var previousCombatReflexDisabled = false

    override fun allowsLoot(item: GroundItem) = false

    override suspend fun awaitActionSafety(safety: ActionSafety): Boolean {
        val remaining = deadline - System.currentTimeMillis()
        if (remaining <= 0) {
            retireUnsettledParent()
            return false
        }
        val allowed = withTimeoutOrNull(remaining) { super.awaitActionSafety(safety) }
        if (allowed == null) retireUnsettledParent()
        return allowed == true
    }

    override suspend fun init(resumed: Boolean): Boolean {
        // pause() requests cancellation before its finally block is dispatched on the game thread.
        while (parent?.isPassive() == false) {
            if (!available()) return true
            if (expired()) {
                // An unsettled cancelled activity must never be restarted over its old cleanup.
                retireUnsettledParent()
                bot.log("Witnessed attack escape expired while waiting for interrupted activity cleanup.")
                return true
            }
            delay(100.milliseconds)
        }
        if (!available() || expired() || !bot.inWilderness()) return true

        previousCombatDisabled = bot.combat.isExplicitlyDisabled
        previousWandering = bot.isWandering
        previousRunning = bot.walking.isRunning
        previousCombatReflexDisabled = bot.reflex.isDisableCombatReflex
        guardsInstalled = true
        suppressAttacks()
        bot.isWandering = false
        bot.walking.isRunning = true
        bot.reflex.isDisableCombatReflex = true
        val zone = bot.scriptStack.findActive(ZonedBotScript::class.java).orElse(null)?.interruptedZone
        bot.lastEscape = BotEscape(observedAt, attacker, System.currentTimeMillis(), zone)
        bot.log("Leaving a witnessed Wilderness attack by $attacker near $observedAt.")
        return false
    }

    override suspend fun run(): Boolean {
        if (!available() || !bot.inWilderness() || expired() || attempts >= 3) return true
        suppressAttacks()
        attempts++
        val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(1)
        val escaped = withTimeoutOrNull(minOf(30.seconds.inWholeMilliseconds, remaining)) {
            escapeAttempt()
        } == true
        if (escaped || !available() || !bot.inWilderness() || expired() || attempts >= 3) return true
        delay(1.seconds)
        return false
    }

    /**
     * Cancels only this attempt when the bot dies, logs out or returns to a connected human session.
     * Native navigation releases its own pending route on cancellation before the script restores its guards.
     */
    private suspend fun escapeAttempt(): Boolean = coroutineScope {
        val flight = async(start = CoroutineStart.UNDISPATCHED) { handler.combat.fleeWilderness() }
        try {
            while (!flight.isCompleted) {
                if (!available()) return@coroutineScope false
                delay(600.milliseconds)
            }
            flight.await()
        } finally {
            withContext(NonCancellable) { flight.cancelAndJoin() }
        }
    }

    /**
     * Keeps native retaliation and any previous combat pursuit from turning escape into a fight.
     */
    private fun suppressAttacks() {
        bot.combat.isDisabled = true
        bot.combat.target = null
        bot.combat.autoRetaliateTarget = null
    }

    /**
     * Whether this script-controlled observer still has a living world session to move.
     */
    private fun available() = bot.state == EntityState.ACTIVE && bot.health > 0 &&
        (!bot.isConnectedPlayer || bot.isAutomationActive) && !bot.client.isPendingLogout

    /**
     * Includes time spent paused or waiting for interrupted activity cleanup in the escape allowance.
     */
    private fun expired() = System.currentTimeMillis() >= deadline

    /**
     * Prevents an interrupted job whose cleanup missed the deadline from being restarted over that cleanup.
     */
    private fun retireUnsettledParent() {
        if (parent?.isPaused() == true && !parent.isPassive()) parent.stop()
    }

    override suspend fun finish() {
        if (!guardsInstalled) return
        // The native attempt's finally and route cancellation have already finished.
        if (bot.combat.isExplicitlyDisabled) bot.combat.isDisabled = previousCombatDisabled
        if (!bot.isWandering) bot.isWandering = previousWandering
        if (bot.walking.isRunning) bot.walking.isRunning = previousRunning
        if (bot.reflex.isDisableCombatReflex) bot.reflex.isDisableCombatReflex = previousCombatReflexDisabled
        guardsInstalled = false
    }
}
