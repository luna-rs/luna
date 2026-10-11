package api.bot

import api.predef.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

/**
 * A [CoroutineDispatcher] implementation that ensures all of our coroutines run on the game thread.
 *
 * @author lare96
 */
object GameCoroutineDispatcher : CoroutineDispatcher() {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        // Dispatch cancellation cleanup to the game thread too.
        val wrapped = {
            try {
                block.run()
            } catch (e: CancellationException) {
                context.cancel(e)
                logger.catching(e)
            } catch (e: Exception) {
                logger.catching(e)
            }
        }
        gameService.gameExecutor.execute(wrapped)
    }
}