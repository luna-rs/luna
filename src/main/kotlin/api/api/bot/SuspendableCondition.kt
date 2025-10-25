package api.bot

import api.predef.*
import api.predef.ext.*
import com.google.common.base.Preconditions.checkState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A utility used to asynchronously wait for a condition to become true within a coroutine.
 *
 * This class schedules a periodic task that evaluates a given [cond] function every tick.
 * When the condition is met, or a timeout is reached, it signals a coroutine to resume via a [Channel].
 *
 * Typically used by bot scripts to wait for in-game events or state changes without blocking the game loop.
 *
 * @param cond The condition that must be satisfied to resume the coroutine.
 * @author lare96
 */
class SuspendableCondition(private val cond: () -> Boolean) {

    /**
     * A one-shot channel used to resume the coroutine when the condition is met or the timeout occurs.
     */
    private val channel = Channel<Boolean>(1)

    /**
     * Indicates whether the task has already been submitted.
     */
    private val active = AtomicBoolean()

    /**
     * Starts monitoring the [cond] periodically and returns a [SuspendableFuture] that can be awaited.
     *
     * The coroutine will be resumed once [cond] is true or after [timeoutSeconds] has passed.
     *
     * @param timeoutSeconds The number of seconds to wait before timing out and unsuspending with failure (default: 120s).
     * @throws IllegalStateException if called more than once.
     * @return A [SuspendableFuture] tied to this condition.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun submit(timeoutSeconds: Long = 30): SuspendableFuture {

        checkState(!active.getAndSet(true), "'submit()' can only be called once.")
        world.schedule(1) {
            // Channel is closed.
            if (channel.isClosedForSend || channel.isClosedForReceive) {
                it.cancel()
                return@schedule
            }
            // Schedule task to check if condition is satisfied.
            if (cond()) {
                channel.offer(true) // Condition satisfied, send unsuspend signal.
                it.cancel()
            } else if (it.executionCounter >= Duration.ofSeconds(timeoutSeconds).toTicks()) {
                channel.offer(false) // Timeout, unsuspend abnormally.
                it.cancel()
            }
        }
        return SuspendableFuture(channel)
    }
}