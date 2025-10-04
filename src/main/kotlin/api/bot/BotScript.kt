package api.bot

import api.bot.action.BotActionHandler
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotBrain
import io.luna.game.model.mob.bot.brain.BotIntelligence
import io.luna.game.model.mob.bot.io.BotInputMessageHandler
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler
import io.luna.game.model.mob.bot.script.BotScriptSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A script type that uses Kotlin coroutines to run logic. Used as a script within a [BotBrain].
 *
 * @author lare96
 */
abstract class BotScript<T>(val bot: Bot) {

    /**
     * The bot intelligence.
     */
    protected val intelligence: BotIntelligence = bot.intelligence

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
        if (progress == null) {
            progress = GameCoroutineScope.launch { run() }
            return true
        }
        return false
    }

    /**
     * Stops this script.
     */
    fun stop(): Boolean {
        if (progress != null) {
            progress!!.cancel()
            progress = null
            return true
        }
        return false
    }
}