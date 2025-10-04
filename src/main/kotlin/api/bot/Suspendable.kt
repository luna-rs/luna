package api.bot

import io.luna.util.RandomUtils
import io.luna.util.Rational
import java.util.concurrent.ThreadLocalRandom
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * An object containing utility methods related to coroutines and suspendable functions.
 *
 * @author lare96
 */
object Suspendable {

    /**
     * Suspends until [cond] is `true` or `duration` elapses. Returns `true` if [cond] was satisfied.
     */
    suspend fun waitFor(duration: Duration = 120.seconds, cond: () -> Boolean) =
        SuspendableCondition(cond, duration.inWholeSeconds).submit().await()

    /**
     * Maybes runs [action] based on [prob]. Returns `true` if the action ran.
     */
    suspend fun maybe(prob: Rational, action: suspend () -> Unit): Boolean {
        if (RandomUtils.roll(prob)) {
            action()
            return true
        }
        return false
    }

    /**
     * Suspends for a random duration within [range].
     */
    suspend fun delay(range: LongRange) {
        kotlinx.coroutines.delay(range.random())
    }

    /**
     * Suspends for [duration].
     */
    suspend fun delay(duration: Duration): Unit = kotlinx.coroutines.delay(duration.inWholeMilliseconds)

    /**
     * Suspends for a random duration between [min] and [max].
     */
    suspend fun delay(min: Duration, max: Duration): Unit {
        val minMs = min.inWholeMilliseconds
        val maxMs = max.inWholeMilliseconds
        delay(minMs..maxMs)
    }

    /**
     * Suspends the current coroutine for a short, natural reaction delay based on the bot’s
     * dexterity and intelligence traits.
     *
     * Faster and smarter bots react more quickly, while slower or less intelligent bots
     * take longer. A small random jitter is always applied to prevent mechanical timing.
     *
     * Typical range:
     * - Minimum: 600 ms
     * - Maximum: 3000 ms
     */
    suspend fun <T> BotScript<T>.naturalDelay() {
        val dexterity = bot.personality.dexterity
        val intelligence = bot.personality.intelligence

        // Compute weighted inverse delay (more dex/int → faster).
        val base = 3000.0 - ((dexterity * 0.6 + intelligence * 0.4) * 2400.0)

        // 10-15% jitter for natural variance.
        val jitterFactor = 1.0 + ThreadLocalRandom.current().nextDouble(-0.15, 0.15)
        val totalDelay = (base * jitterFactor).coerceIn(600.0, 3000.0).toLong()

        delay(totalDelay.milliseconds)
    }

    /**
     * Suspends the current coroutine for a longer, cognitive delay based on the bot’s
     * intelligence and dexterity traits.
     *
     * Used for deliberate or strategic actions such as deciding dialogue, pathfinding,
     * or reacting to complex stimuli. Smarter bots make decisions faster and with
     * less variance, while less intelligent bots pause for longer.
     *
     * Typical range:
     * - Minimum: 1200 ms
     * - Maximum: 10 000 ms
     */
    suspend fun <T> BotScript<T>.naturalDecisionDelay() {
        val dexterity = bot.personality.dexterity.coerceIn(0.0, 1.0)
        val intelligence = bot.personality.intelligence.coerceIn(0.0, 1.0)

        // Heavier weight on intelligence for decision latency
        val base = 10_000.0 - ((intelligence * 0.7 + dexterity * 0.3) * 8800.0)

        // Always include jitter for realism
        val jitterFactor = 1.0 + ThreadLocalRandom.current().nextDouble(-0.1, 0.1)
        val totalDelay = (base * jitterFactor).coerceIn(1200.0, 10_000.0).toLong()

        delay(totalDelay.milliseconds)
    }
}

