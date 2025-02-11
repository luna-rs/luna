package api.bot

import api.bot.action.BotActionHandler
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.BotScript
import io.luna.game.model.mob.bot.BotInputMessageHandler
import io.luna.game.model.mob.bot.BotOutputMessageHandler
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
     * The bot input handler.
     */
    protected val input: BotInputMessageHandler = bot.botClient.input

    /**
     * The bot output handler.
     */
    protected val output: BotOutputMessageHandler = bot.botClient.output

    /**
     * The action handler.
     */
    protected val handler: BotActionHandler = BotActionHandler(bot)

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