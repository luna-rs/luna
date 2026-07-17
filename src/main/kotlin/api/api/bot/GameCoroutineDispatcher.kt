package api.bot

import api.predef.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.isActive
import kotlin.coroutines.CoroutineContext

/**
 * A [CoroutineDispatcher] implementation that ensures all of our coroutines run on the game thread.
 *
 * @author lare96
 */
object GameCoroutineDispatcher : CoroutineDispatcher() {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        // Dispatch back to the game thread to run safely.
        if (context.isActive) {
            gameService.gameExecutor.execute(block)
        }
    }
}