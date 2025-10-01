package api.bot

import io.luna.util.RandomUtils
import io.luna.util.Rational
import kotlin.time.Duration
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
        if (RandomUtils.rollSuccess(prob)) {
            action()
            return true
        }
        return false
    }

    /**
     * Suspends for a random duration within [range].
     */
    suspend fun delay(range: IntRange) {
        delay(range.first.toLong()..range.last.toLong())
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
     * Suspends for the default throttling duration, a random value between 600-1800ms. Use this to naturally delay
     * bot actions or thinking.
     */
    suspend fun delay() = delay(600..1800)
}

