package api.bot.script

import api.bot.action.BotActionHandler
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.io.BotInputMessageHandler
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler
import kotlinx.coroutines.Job

/**
 * Base class for all bot scripts.
 *
 * A bot script owns the high-level behavior for a [Bot] and provides convenient access to the bot's simulated client
 * input, simulated client output, reusable action handler, and coroutine lifecycle state.
 *
 * Scripts should use [input], [output], and [handler] instead of reaching through the bot client directly. This keeps
 * bot behavior close to the same path used by real player actions.
 *
 * @property bot The bot controlled by this script.
 * @author lare96
 */
abstract class AbstractBotScript(val bot: Bot) {

    /**
     * Handles simulated inbound client messages for this bot.
     *
     * The input handler feeds actions into the same general pipeline used by player-originated client messages, such as
     * walking, button clicks, item clicks, entity interactions, and widget input.
     */
    protected val input: BotInputMessageHandler = bot.botClient.input

    /**
     * Handles simulated outbound client messages for this bot.
     *
     * The output handler lets scripts send packet-style actions through the bot client, allowing scripted behavior to
     * interact with the world through the same broad flow as a real player.
     */
    protected val output: BotOutputMessageHandler = bot.botClient.output

    /**
     * Provides high-level reusable bot actions.
     *
     * The action handler groups common bot behavior such as banking, shopping, equipment changes, inventory operations,
     * widget clicks, entity interactions, navigation support, and combat utility actions.
     */
    protected val handler: BotActionHandler = bot.actionHandler

    /**
     * The coroutine job for this script's active or most recent execution.
     *
     * This is `null` until the script has been started at least once. After a script receives a job, the reference is
     * kept even after the job completes so lifecycle checks can distinguish a never-started script from one that already
     * ran and finished.
     */
    protected var progress: Job? = null

    /**
     * Returns whether this script has never been assigned an execution job.
     *
     * This does not mean the script is currently paused or stopped. A script that already ran keeps its most recent
     * [progress] reference, even when that job is no longer active.
     *
     * @return `true` if this script has never been assigned a job.
     */
    fun isIdle(): Boolean {
        return progress == null
    }

    /**
     * Returns whether this script is currently executing.
     *
     * @return `true` if [progress] points to an active coroutine job.
     */
    fun isRunning(): Boolean {
        return progress?.isActive == true
    }
}