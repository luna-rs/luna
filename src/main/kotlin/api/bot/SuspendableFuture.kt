package api.bot

import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Future

/**
 * A model similar to a Java [Future] types, used to return the result of suspendable jobs within
 * [CoroutineBotScript] types. This model can also be used to block coroutines until a job completes or a certain
 * condition is satisfied.
 */
class SuspendableFuture(private val channel: Channel<Boolean>) {

    /**
     * No-args constructor for futures with no conditional signal.
     */
    constructor() : this(Channel<Boolean>(1))

    /**
     * Sends a signal to the backing [channel] in order to unsuspend the underlying [CoroutineBotScript].
     */
    internal fun signal(value: Boolean): SuspendableFuture {
        channel.offer(value)
        return this
    }

    /**
     * Suspends the underlying [CoroutineBotScript] until a signal is received.
     *
     * Returns `true` if the signal was received successfully, `false` on timeout, error, or illegal state.
     */
    suspend fun await(): Boolean {
        val result = channel.receive()
        channel.close()
        return result
    }
}