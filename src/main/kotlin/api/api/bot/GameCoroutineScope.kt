package api.bot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * The global coroutine scope for the game engine.
 * <p>
 * All coroutines launched on this scope are tied to the server lifecycle and will be cancelled when the
 * server shuts down.
 */
object GameCoroutineScope : CoroutineScope {

    /**
     * Prevents one child failure from cancelling the entire scope.
     */
    private val job = SupervisorJob()

    override val coroutineContext = job + GameCoroutineDispatcher

    /**
     * Cancels all coroutines running in this scope.
     */
    fun shutdown() {
        job.cancel()
    }
}