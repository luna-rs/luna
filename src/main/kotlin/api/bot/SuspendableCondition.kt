package api.bot

import api.predef.*
import api.predef.ext.*
import com.google.common.base.Preconditions.checkState
import io.luna.game.task.TaskManager
import kotlinx.coroutines.channels.Channel
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A model that acts as a precursor to [SuspendableFuture]. It allows for the conditional suspension of coroutines
 * based on if [cond] has been satisfied. Once this type has been submitted, the [TaskManager] repeatedly checks the
 * condition and sends an unsuspend signal once satisfied.
 */
class SuspendableCondition(private val cond: () -> Boolean, private val timeoutSeconds: Long = 120) {

    /**
     * The channel that will be used to suspend the coroutine. Acts like a [Phaser] for coroutines.
     */
    private val channel = Channel<Boolean>(1)

    /**
     * Determines if this is active.
     */
    private val active = AtomicBoolean()

    /**
     * Submits a task to continually check if [cond] is satisfied; if it is, sends a signal in order for the channel to
     * unsuspend the underlying [CoroutineBotScript]. Times out after [timeoutSeconds]. **Does not suspend the coroutine.**
     */
    fun submit(): SuspendableFuture {
        checkState(!active.getAndSet(true), "'submit()' can only be called once.")
        world.schedule(1) {
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