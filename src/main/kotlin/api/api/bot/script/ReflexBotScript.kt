package api.bot.script

import api.bot.GameCoroutineScope
import io.luna.game.model.EntityState
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * A short-lived reactive script that runs while it remains the bot's active reflex.
 *
 * Reflex scripts are intended for urgent behaviour that temporarily interrupts normal script execution, such as fleeing,
 * eating, responding to danger, avoiding death, or handling another immediate condition detected by the bot's reflex
 * system.
 *
 * Unlike normal bot scripts, a reflex script does not own the bot's long-term activity. It runs only while it is still
 * assigned as the active reflex, the bot is active, and its coroutine has not been cancelled.
 *
 * @param bot The bot running this reflex script.
 * @author lare96
 */
abstract class ReflexBotScript(bot: Bot) : AbstractBotScript(bot) {

    /**
     * Returns whether this reflex should activate.
     *
     * This should be a cheap check because reflex logic may be evaluated often by the bot's decision system.
     *
     * @return `true` if this reflex should become active.
     */
    abstract fun shouldReact(): Boolean

    /**
     * Performs one reflex execution cycle.
     *
     * This is called repeatedly while the reflex remains active. Returning `true` completes the reflex and exits the
     * coroutine loop. Returning `false` keeps the reflex running until it completes, is replaced, the bot becomes
     * inactive, or the coroutine is cancelled.
     *
     * @return `true` when this reflex has finished.
     */
    abstract suspend fun run(): Boolean

    /**
     * Starts this reflex script if it is not already running.
     *
     * The script runs in [GameCoroutineScope] and continues until one of the following happens:
     *
     * - [run] returns `true`.
     * - This script is no longer [bot.reflex.activeReflex].
     * - The bot is no longer [EntityState.ACTIVE].
     * - The coroutine is cancelled.
     */
    fun start() {
        if (progress?.isActive != true) {
            bot.log("Running reflex script {${javaClass.name}}.")

            progress =
                GameCoroutineScope.launch {
                    while (bot.reflex.activeReflex == this@ReflexBotScript && bot.state == EntityState.ACTIVE && isActive) {
                        yield()
                        if (run()) {
                            break
                        }
                    }
                }
        }
    }

    /**
     * Stops this reflex script.
     *
     * Cancelling the current coroutine prevents future [run] cycles from executing.
     */
    fun stop() {
        progress?.cancel()
    }
}