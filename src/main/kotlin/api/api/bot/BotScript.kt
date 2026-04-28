package api.bot

import api.bot.action.BotActionHandler
import io.luna.game.model.EntityState
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.io.BotInputMessageHandler
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler
import io.luna.game.model.mob.bot.script.BotScriptSnapshot
import io.luna.game.model.mob.bot.script.BotScriptStack
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * A coroutine-driven base class for bot behaviour scripts.
 *
 * A script represents one logical bot behaviour, such as woodcutting, fishing, combat, or PKing (scouting). Scripts
 * are controlled by [BotScriptStack], executed on the game coroutine scope (game thread), and may safely suspend while
 * waiting for delays, navigation, interactions, or other asynchronous bot actions.
 *
 * Scripts follow this lifecycle:
 *
 * 1. [start] launches the script coroutine.
 * 2. [init] runs once before the main loop.
 * 3. [run] is called repeatedly while the bot is active.
 * 4. Returning `true` from [run] finishes the script.
 * 5. [finish] runs before the coroutine exits, including after cancellation.
 *
 * The [snapshot] function is used to expose persistent script state so scripts can be saved and restored across
 * sessions.
 *
 * @param T The snapshot type used to persist this script's state.
 * @property bot The bot controlled by this script.
 *
 * @author lare96
 */
abstract class BotScript<T>(val bot: Bot) {

    /**
     * Handler for simulated inbound client messages.
     *
     * This gives scripts access to the same input pipeline that a normal player client would use for actions such as
     * button clicks, movement requests, item clicks, and other client-originated messages.
     */
    protected val input: BotInputMessageHandler = bot.botClient.input

    /**
     * Handler for simulated outbound client messages.
     *
     * This lets scripts send packet-style actions through the bot client, allowing the bot to interact with the world
     * through the same broad flow as a real player.
     */
    protected val output: BotOutputMessageHandler = bot.botClient.output

    /**
     * High-level action handler for common bot operations.
     *
     * This groups reusable bot actions such as banking, shopping, equipment changes, inventory interactions, widget
     * clicks, entity interactions, and combat utility actions.
     */
    protected val handler: BotActionHandler = bot.actionHandler

    /**
     * Coroutine job tracking this script's current execution state.
     *
     * This value is `null` before the script has ever been started. Once [start] launches the script, this stores the
     * active job. If the script finishes, is cancelled, or is restarted later, this value reflects the most recent job.
     */
    private var progress: Job? = null

    /**
     * Initializes this script before the main [run] loop begins.
     *
     * This is called once per successful [start] invocation, before the first [run] call. Subclasses can override this
     * to prepare state, start navigation, attack an initial target, open an interface, or perform any other setup.
     */
    open suspend fun init() {}

    /**
     * Executes one cycle of this script.
     *
     * This function is called repeatedly while the bot remains [EntityState.ACTIVE] and the script coroutine remains
     * active. Returning `false` keeps the script alive and allows the next cycle to run. Returning `true` breaks the
     * loop and lets the script finish normally.
     *
     * Implementations may suspend freely, but should avoid long blocking work because scripts execute through the game
     * coroutine system.
     *
     * @return `true` if the script should finish, otherwise `false` to keep looping.
     */
    abstract suspend fun run(): Boolean

    /**
     * Finalizes this script before its coroutine exits.
     *
     * This is called from a `finally` block after the main loop ends, so it runs after normal completion and after
     * cancellation. Subclasses can override this to clear temporary state, close interfaces, stop movement, reset flags,
     * or release resources.
     */
    open suspend fun finish() {}

    /**
     * Produces a snapshot of this script's persistent state.
     *
     * The returned value is wrapped in a [BotScriptSnapshot] and stored with the owning bot so the script can be
     * restored later.
     *
     * Implementations should only capture minimal serializable state. Avoid storing live world objects, active
     * coroutine state, or references that cannot safely survive a restart.
     *
     * @return A snapshot representing the current persistent state of this script.
     */
    abstract fun snapshot(): T

    /**
     * Starts this script.
     *
     * If the script is already active, this method does nothing and returns `false`. Otherwise, it launches a new
     * coroutine, calls [init], repeatedly calls [run], and finally calls [finish] before the coroutine exits.
     *
     * @return `true` if a new script coroutine was started, otherwise `false`.
     */
    fun start(): Boolean {
        if (progress?.isActive != true) {
            bot.log("Running script {${javaClass.name}}.")
            progress = GameCoroutineScope.launch {
                try {
                    init()
                    while (bot.state == EntityState.ACTIVE && isActive) {
                        yield()
                        if (run()) {
                            break
                        }
                    }
                } finally {
                    finish()
                }
            }
            return true
        }
        return false
    }

    /**
     * Stops this script by cancelling its active coroutine.
     *
     * Cancellation causes the running coroutine to exit through its `finally` block, so [finish] is still called. If
     * the script is not currently running, this method does nothing.
     *
     * @return `true` if the script was running and cancellation was requested, otherwise `false`.
     */
    fun stop(): Boolean {
        if (progress != null && !progress!!.isCompleted && !progress!!.isCancelled) {
            bot.log("Stopping script {${javaClass.name}}.")
            progress?.cancel()
            return true
        }

        return false
    }

    /**
     * Returns whether this script completed normally.
     *
     * A script is considered finished when it has a job, that job completed, and it was not cancelled.
     *
     * @return `true` if the script completed without cancellation, otherwise `false`.
     */
    fun isFinished(): Boolean {
        return progress != null && progress!!.isCompleted && !progress!!.isCancelled
    }

    /**
     * Returns whether this script is currently running.
     *
     * @return `true` if the script coroutine is active, otherwise `false`.
     */
    fun isRunning(): Boolean {
        return progress?.isActive == true
    }

    /**
     * Returns whether this script was interrupted.
     *
     * A script is considered interrupted when its most recent job was cancelled.
     *
     * @return `true` if the script was cancelled, otherwise `false`.
     */
    fun isInterrupted(): Boolean {
        return progress?.isCancelled == true
    }

    /**
     * Returns whether this script has never been started.
     *
     * This only checks whether a job has ever been assigned. A stopped, cancelled, or completed script is not idle
     * because [progress] still references the most recent job.
     *
     * @return `true` if this script has no job, otherwise `false`.
     */
    fun isIdle(): Boolean {
        return progress == null
    }
}