package api.bot

import api.bot.action.BotActionHandler
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotBrain
import io.luna.game.model.mob.bot.brain.BotIntelligence
import io.luna.game.model.mob.bot.io.BotInputMessageHandler
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler
import io.luna.game.model.mob.bot.script.BotScriptSnapshot
import io.luna.game.model.mob.bot.speech.BotSpeechContext
import io.luna.util.RandomUtils
import io.luna.util.Rational
import kotlinx.coroutines.Delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

/**
 * A script type that uses Kotlin coroutines to run logic. Used as a script within a [BotBrain].
 *
 * @author lare96
 */
abstract class BotScript<T>(protected val bot: Bot) {

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
    private val progress = AtomicReference<Job>()

    /**
     * Runs the logic within a coroutine.
     */
    abstract suspend fun run()

    /**
     * Defines the context in which a bot speaks. Subsequent invocations do not have to return the same result.
     *
     * @return The speech context.
     */
    abstract fun speechContext(): BotSpeechContext

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
        return progress.compareAndSet(null, GlobalScope.launch(Dispatchers.Unconfined) { run() })
    }
}