package api.bot

import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.BotMessageHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * A script type that uses Kotlin coroutines to run logic. Commonly used as a subscript within a master [BotScript].
 */
abstract class CoroutineBotScript(protected val bot: Bot) {

    /**
     * The bot actions.
     */
    protected val botActions: BotMessageHandler = bot.messageHandler

    /**
     * The progress of this script.
     */
    private val progress = AtomicReference<Job>()

    /**
     * Runs the logic within a coroutine.
     */
    abstract suspend fun run()

    /**
     * Starts this coroutine bot script.
     */
    internal fun start(): Boolean {
        return progress.compareAndSet(null, GlobalScope.launch(Dispatchers.Unconfined) { run() })
    }

    /**
     * Determines if this coroutine has completed.
     */
    fun isDone(): Boolean {
        return progress.get() != null && progress.get().isCompleted
    }

    /**
     * Determines if [Job.isActive].
     */
    fun isActive(): Boolean {
        return progress.get() != null && progress.get().isActive
    }
}