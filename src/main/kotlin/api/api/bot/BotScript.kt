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
 * A coroutine-driven base class for bot behavior scripts.
 *
 * A script represents one logical bot behavior, such as woodcutting, fishing, combat, banking, trading, travelling,
 * or PK scouting. Scripts are owned by a [BotScriptStack], executed on the game coroutine scope, and may suspend while
 * waiting for navigation, interactions, delays, widgets, or other bot actions.
 *
 * Scripts follow this lifecycle:
 * 1. [start] launches the script coroutine.
 * 2. [init] runs before the main loop.
 * 3. [run] is called repeatedly while the bot is active.
 * 4. Returning `true` from [run] finishes the script normally.
 * 5. [finish] runs before the coroutine exits, including after pause or termination cancellation.
 *
 * Scripts can be paused and later resumed. When a paused script is started again, [init] receives `resumed = true`,
 * allowing subclasses to rebuild temporary state before continuing.
 *
 * The [snapshot] function exposes persistent script state so scripts can be saved and restored across sessions.
 *
 * @param T The snapshot type used to persist this script's state.
 * @property bot The bot controlled by this script.
 * @author lare96
 */
abstract class BotScript<T>(val bot: Bot) {

    /**
     * Handles simulated inbound client messages for this bot.
     *
     * This gives scripts access to the same input pipeline used by normal player-originated actions, such as movement
     * requests, button clicks, item clicks, entity interactions, and widget input.
     */
    protected val input: BotInputMessageHandler = bot.botClient.input

    /**
     * Handles simulated outbound client messages for this bot.
     *
     * This lets scripts send packet-style actions through the bot client, allowing the bot to interact with the world
     * through the same broad flow as a real player.
     */
    protected val output: BotOutputMessageHandler = bot.botClient.output

    /**
     * Provides high-level reusable bot actions.
     *
     * This groups common behavior such as banking, shopping, equipment changes, inventory operations, widget clicks,
     * entity interactions, navigation support, and combat utility actions.
     */
    protected val handler: BotActionHandler = bot.actionHandler

    /**
     * The coroutine job for this script's most recent execution.
     *
     * This is `null` until the script is started for the first time. After [start] is called, this points at the
     * active or most recently completed job. Paused, finished, and terminated scripts keep their most recent job
     * reference so lifecycle checks can distinguish idle scripts from scripts that have already run.
     */
    private var progress: Job? = null

    /**
     * Whether this script has been permanently terminated.
     *
     * Paused scripts may be started again by [start]. Terminated scripts cannot be restarted, even if
     * their coroutine has completed or been cancelled.
     */
    private var terminated = false

    /**
     * Initializes this script before the main [run] loop begins.
     *
     * This is called once per successful [start] invocation. If the script was previously paused and is being started
     * again, [resumed] will be `true`.
     *
     * Subclasses can override this to rebuild temporary state, start navigation, select an initial target, open an
     * interface, or perform other setup.
     *
     * Returning `true` skips the main [run] loop and allows the script coroutine to finish immediately.
     * Returning `false` allows normal script execution to continue.
     *
     * @param resumed `true` if this script is being restarted after a pause, otherwise `false`.
     * @return `true` to finish during initialization, or `false` to enter the main [run] loop.
     */
    open suspend fun init(resumed: Boolean): Boolean = false

    /**
     * Executes one cycle of this script.
     *
     * This function is called repeatedly while the bot remains [EntityState.ACTIVE] and this script's coroutine
     * remains active. Returning `false` keeps the script alive and allows another cycle to run.
     *
     * Returning `true` exits the loop and lets the script finish normally.
     *
     * Implementations may suspend freely, but should avoid blocking work because scripts execute through the game
     * coroutine system.
     *
     * @return `true` if the script should finish, otherwise `false` to keep running.
     */
    abstract suspend fun run(): Boolean

    /**
     * Finalizes this script before its coroutine exits.
     *
     * This is called from a `finally` block, so it runs after normal completion, initialization exit, pause cancellation,
     * and termination cancellation.
     *
     * Subclasses can override this to clear temporary state, close interfaces, stop movement, reset flags,
     * release resources, or undo script-specific context.
     */
    open suspend fun finish() {}

    /**
     * Called immediately before a running script is paused.
     *
     * This hook is invoked by [pause] before the active coroutine is cancelled. Subclasses can override it to save
     * volatile state, stop temporary behavior, clear local targets, or mark that the script should rebuild
     * context when resumed.
     */
    open fun paused() {}

    /**
     * Produces a snapshot of this script's persistent state.
     *
     * The returned value is wrapped in a [BotScriptSnapshot] by the owning [BotScriptStack] and stored
     * with the bot so the script can be restored later.
     *
     * Implementations should capture minimal serializable state only. Avoid storing live world objects,
     * active coroutine state, entity references, or other runtime-only objects that cannot safely survive
     * logout, restart, or reload.
     *
     * @return A snapshot representing this script's persistent state.
     */
    abstract fun snapshot(): T

    /**
     * Starts this script if it is not already running and has not been terminated.
     *
     * Starting launches a new coroutine on [GameCoroutineScope]. The coroutine calls [init], then loops
     * through [run] until the bot becomes inactive, the coroutine is cancelled, or [run] returns `true`.
     * [finish] is always called before the coroutine exits.
     *
     * If this script was previously paused, [init] receives `resumed = true`. If this script is already running or has
     * been permanently terminated, no new coroutine is launched.
     *
     * @return `true` if a new script coroutine was started, otherwise `false`.
     */
    fun start(): Boolean {
        if (progress?.isActive != true && !terminated) {
            val wasPaused = isPaused()
            bot.log("Running script {${javaClass.name}}.")
            progress = GameCoroutineScope.launch {
                try {
                    if (init(wasPaused)) {
                        return@launch
                    }
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
     * Pauses this script by cancelling its active coroutine.
     *
     * Pausing is temporary. A paused script can be started again later, and [init] will receive `resumed = true` on
     * the next successful [start].
     *
     * Cancellation causes the coroutine to exit through its `finally` block, so [finish] is still called.
     * If this script is not currently running, this method does nothing.
     *
     * @return `true` if the script was running and pause cancellation was requested, otherwise `false`.
     */
    fun pause(): Boolean {
        if (isRunning()) {
            bot.log("Pausing script {${javaClass.name}}.")
            paused()
            progress?.cancel()
            return true
        }
        return false
    }

    /**
     * Permanently stops this script by cancelling its active coroutine and preventing future restarts.
     *
     * Unlike [pause], termination is final for this script instance. Once terminated, [start] will no longer launch
     * a coroutine for it.
     *
     * Cancellation still exits through the coroutine's `finally` block, so [finish] is called.
     *
     * @return `true` if the script wasn't already terminated and termination was requested, otherwise `false`.
     */
    fun terminate(): Boolean {
        if (!terminated) {
            bot.log("Terminating script {${javaClass.name}}.")
            progress?.cancel()
            terminated = true
            return true
        }
        return false
    }

    /**
     * Returns whether this script completed normally.
     *
     * A script is considered finished when it has a job, that job has completed, and that job was not
     * cancelled. Paused and terminated scripts are not considered finished because both states cancel the
     * active coroutine.
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
     * Returns whether this script is currently paused.
     *
     * A script is considered paused when its most recent coroutine job was cancelled, and it's not terminated.
     *
     * @return `true` if the most recent script job was cancelled and this script hasn't been terminated, otherwise `false`.
     */
    fun isPaused(): Boolean {
        return progress?.isCancelled == true && !terminated
    }

    /**
     * Returns whether this script has never been started.
     *
     * This only checks whether a coroutine job has ever been assigned. A completed, paused, or terminated script is
     * not idle because [progress] still references the most recent job.
     *
     * @return `true` if this script has no job, otherwise `false`.
     */
    fun isIdle(): Boolean {
        return progress == null
    }
}