package api.bot

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Future
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/**
 * A coroutine-friendly future used to await the result of asynchronous bot logic in [BotScript]s.
 *
 * This acts like a simplified, suspending version of Java's [Future], used to block a coroutine
 * until a condition is met or a task completes. Most commonly returned by [SuspendableCondition] to
 * allow coroutine-based bots to suspend execution until they are
 * signaled to resume.
 *
 * @param channel The internal one-time signal channel used for suspension and resumption.
 * @author lare96
 */
open class SuspendableFuture(private val channel: Channel<Boolean>) {

    object SuspendableFutureFailed : SuspendableFuture() {
        init {
            signal(false)
        }

        override suspend fun await(): Boolean {
            return false
        }
    }

    object SuspendableFutureSuccess : SuspendableFuture() {
        init {
            signal(true)
        }

        override suspend fun await(): Boolean {
            return true
        }
    }

    /**
     * Secondary constructor for creating an empty future that can be manually signaled.
     */
    constructor() : this(Channel<Boolean>(1))

    /**
     * Sends a signal to the coroutine waiting on this future.
     *
     * @param value `true` if the condition was met or task succeeded, `false` for timeout or failure.
     * @return This future for chaining.
     */
    internal fun signal(value: Boolean): SuspendableFuture {
        channel.offer(value)
        return this
    }

    /**
     * Suspends the coroutine until a signal is received via the internal channel.
     *
     * @return `true` if the signal indicated success, `false` if it timed out, failed, or closed abnormally.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    open suspend fun await(): Boolean {
        // Cancel scheduled task when coroutine is cancelled.
        coroutineContext[Job]?.invokeOnCompletion {
            if (!channel.isClosedForSend && !channel.isClosedForReceive) {
                channel.close()
            }
        }

        // Await signal, handle potentially errors.
        return try {
            val result = channel.receive()
            channel.close()
            result
        } catch (e: CancellationException) {
            channel.close()
            false
        }
    }
}