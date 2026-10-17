
package api.bot

import api.predef.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

/**
 * A [CoroutineDispatcher] that schedules coroutine execution through Luna's game-thread services.
 *
 * Regular coroutine continuations are submitted to the game executor, while yielded continuations
 * are scheduled through the game service's yield mechanism. Both dispatch paths wrap their tasks
 * to handle exceptions during execution.
 *
 * Cancellation exceptions cancel the associated coroutine context and are logged. Other exceptions
 * are logged without explicitly cancelling the context.
 *
 * @author lare96
 */
object GameCoroutineDispatcher : CoroutineDispatcher() {

    /**
     * Schedules a coroutine continuation for execution on the game thread.
     *
     * Submits the continuation to the game executor through [wrap], ensuring exceptions thrown
     * during execution are handled by the dispatcher's exception wrapper.
     *
     * @param context The coroutine context associated with the continuation.
     * @param block The continuation to execute.
     */
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        // Dispatch cancellation cleanup to the game thread too.
        gameService.gameExecutor.execute(wrap(context, block))
    }

    /**
     * Schedules a yielded coroutine continuation through the game service.
     *
     * Uses the game service's yield mechanism rather than the regular executor dispatch path.
     * The continuation is wrapped with the same exception handling used by [dispatch].
     *
     * @param context The coroutine context associated with the yielded continuation.
     * @param block The continuation to execute after yielding.
     */
    @OptIn(InternalCoroutinesApi::class)
    override fun dispatchYield(context: CoroutineContext, block: Runnable) {
        gameService.yield(wrap(context, block))
    }

    /**
     * Wraps a coroutine continuation with exception handling.
     *
     * Executes the continuation and catches any exceptions it throws. A [CancellationException]
     * cancels the associated coroutine context and is logged. Other [Throwable] instances are
     * logged without being rethrown or explicitly cancelling the context.
     *
     * @param context The coroutine context to cancel if a cancellation exception occurs.
     * @param block The continuation to execute.
     * @return A runnable that executes the continuation with exception handling.
     */
    private fun wrap(context: CoroutineContext, block: Runnable): Runnable =
        Runnable {
            try {
                block.run()
            } catch (e: CancellationException) {
                context.cancel(e)
                logger.catching(e)
            } catch (e: Throwable) {
                logger.catching(e)
            }
        }
}
