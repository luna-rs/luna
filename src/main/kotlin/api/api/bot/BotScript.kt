package api.bot

import api.bot.action.BotActionHandler
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.io.BotInputMessageHandler
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler
import io.luna.game.model.mob.bot.script.BotScriptSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A base script type using Kotlin coroutines to execute bot logic.
 *
 * Every [BotScript] holds the bot instance and a snapshot of input data for persistence.
 */
abstract class BotScript<T>(val bot: Bot) {

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
    protected val handler: BotActionHandler = bot.actionHandler

    /**
     * The progress of this script.
     */
    private var progress: Job? = null

    /**
     * Runs the logic within a coroutine.
     */
    abstract suspend fun run()

    /**
     * Builds a snapshot of this script to be saved to the character file of [bot]. This ensures persistence between
     * server launches.
     */
    abstract fun snapshot(): T

    /**
     * Loads a snapshot into memory, so the script can resume where it left off.
     */
    abstract fun load(snapshot: BotScriptSnapshot<T>)

    /**
     * Starts this script.
     */
    fun start(): Boolean {
        if (progress == null || progress!!.isCancelled) {
            bot.log("Running script {${javaClass.name}}.")
            progress = GameCoroutineScope.launch { run() }
            return true
        }
        return false
    }

    /**
     * Stops this script.
     */
    fun stop(): Boolean {
        if (progress != null && !progress!!.isCompleted && !progress!!.isCancelled) {
            bot.log("Stopping script {${javaClass.name}}.")
            progress!!.cancel()
            progress = null
            return true
        }
        return false
    }

    /**
     * Determines if this script has completed normally.
     */
    fun isFinished(): Boolean {
        return progress != null && progress!!.isCompleted && !progress!!.isCancelled
    }

     /**
     * Determines if this script is running.
     */
    fun isRunning(): Boolean {
        return progress != null && progress!!.isActive
    }

    /**
     * Determines if this script was interrupted.
     */
    fun isInterrupted(): Boolean {
        return progress != null && progress!!.isCancelled
    }

    /**
     * Determines if this script is waiting to be started.
     */
    fun isIdle(): Boolean {
        return progress == null
    }
}