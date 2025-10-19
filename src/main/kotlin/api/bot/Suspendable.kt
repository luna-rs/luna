package api.bot

import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.io.BotInputMessageHandler
import io.luna.net.msg.GameMessageWriter
import io.luna.util.RandomUtils
import io.luna.util.Rational
import java.util.concurrent.ThreadLocalRandom
import kotlin.reflect.KClass
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
        SuspendableCondition(cond).submit(duration.inWholeSeconds.coerceAtLeast(1)).await()

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
     * Suspends the current coroutine for a short, natural reaction delay.
     *
     * Typical range:
     * - Minimum: 600 ms
     * - Maximum: 3000 ms
     */
    suspend fun Bot.naturalDelay() {
        return delay(600.milliseconds, 3.seconds)
    }

    /**
     * Suspends the current coroutine for a longer, cognitive delay.
     *
     * Typical range:
     * - Minimum: 1200 ms
     * - Maximum: 6000 ms
     */
    suspend fun Bot.naturalDecisionDelay() {
        delay(1200.milliseconds, 6.seconds)
    }

    /**
     * Waits for a message of the specified [type] to arrive that satisfies the given [cond].
     *
     * This suspends the coroutine until:
     *  - A matching message arrives and the condition returns true.
     *  - Or the [timeoutSeconds] elapses.
     *
     * @param timeoutSeconds How long to wait before timing out. Default = 5 minutes (300s).
     * @param type The message class type to wait for.
     * @param cond A predicate function that evaluates incoming messages.
     * @return True if the condition was satisfied before timing out, false otherwise.
     */
    suspend fun <T : GameMessageWriter> BotInputMessageHandler.waitFor(timeoutSeconds: Long = 300, type: KClass<T>,
                                                                       cond: T.() -> Boolean): Boolean {
        // Drop any messages from before we started waiting.
        received[type.java].clear()

        val condition = SuspendableCondition {
            val messages = received[type.java]
            val iterator = messages.iterator()
            while (iterator.hasNext()) {
                val msg = iterator.next()

                @Suppress("UNCHECKED_CAST")
                val typedMsg = msg.message as? T ?: continue

                if (cond(typedMsg)) {
                    // Found the message that matches the condition.
                    return@SuspendableCondition true
                }
                // Otherwise remove from buffer.
                iterator.remove()
            }
            false
        }
        if (!condition.submit(timeoutSeconds).await()) {
            bot.log("Timed out waiting for input message {${type.simpleName}}.")
            return false
        }
        return true
    }
}

