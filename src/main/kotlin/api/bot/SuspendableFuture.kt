package api.bot

import api.predef.*
import api.predef.ext.*
import com.google.common.base.Preconditions.checkState
import kotlinx.coroutines.channels.Channel
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Represents the result of requesting a bot to do an action. These types differ from Java futures in that instead of
 * returning an Object in the future, they allow for suspension of the underlying coroutine.
 */
class SuspendableFuture {

    companion object {

        /**
         * Run [runFunc] every [delayTicks] and suspend the underlying coroutine until [cond] is satisfied.
         */
        suspend fun runUntil(delayTicks: Int, runFunc: () -> Unit, cond: () -> Boolean) {
            runFunc()
            if (cond()) {
                return
            }

            val future = SuspendableFuture()
            world.schedule(delayTicks) {
                if (cond()) {
                    future.signal(true)
                    it.cancel()
                } else {
                    runFunc()
                }
            }
            future.await()
        }
    }

    /**
     * The channel that will be used to suspend the coroutine. Acts like a [Phaser] for coroutines.
     */
    private val channel = Channel<Boolean>(1)

    /**
     * Determines if this future is active.
     */
    private val active = AtomicBoolean()

    /**
     * Checks if [cond] is satisfied; if it is, sends [signal] in order for [await] to unsuspend the underlying
     * [CoroutineBotScript]. Times out after [timeoutSeconds]. **Does not suspend the coroutine.**
     */
    fun signalWhen(timeoutSeconds: Long, cond: () -> Boolean): SuspendableFuture {
        checkState(!active.getAndSet(true), "SuspendableFuture is already active.")
        world.schedule(1) {
            if (cond()) {
                channel.offer(true)
                it.cancel()
            } else if (it.executionCounter >= Duration.ofSeconds(timeoutSeconds).toTicks()) {
                channel.offer(false)
                it.cancel()
            }
        }
        return this
    }

    /**
     * Suspends the underlying [CoroutineBotScript] until a signal from either [signal] or [signalWhen] is received.
     */
    suspend fun await(): Boolean = channel.receive()

    /**
     * Sends a signal to [await] to unsuspend the underlying [CoroutineBotScript]. [value] being false indicates an
     * abnormal signal (exception, time out, etc). **Does not suspend the coroutine.**
     */
    fun signal(value: Boolean): SuspendableFuture {
        checkState(!active.getAndSet(true), "SuspendableFuture is already active.")
        channel.offer(value)
        return this
    }
}