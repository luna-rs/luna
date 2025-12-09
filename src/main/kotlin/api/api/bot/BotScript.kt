package api.bot

import api.bot.action.BotActionHandler
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.io.BotInputMessageHandler
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler
import io.luna.game.model.mob.bot.script.BotScriptSnapshot
import io.luna.game.model.mob.bot.script.BotScriptStack
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A coroutine-driven base class for bot behaviour scripts.
 *
 * Each [BotScript] represents a single logical “program” that drives a [bot] for some period of time
 * (e.g., woodcutting, banking, combat loop). Scripts are:
 * - Executed on the game thread via a shared [GameCoroutineScope] and [GameCoroutineDispatcher].
 * - Managed by [BotScriptStack], which controls which script is currently active.
 * - Persisted and restored across sessions using [BotScriptSnapshot] and [snapshot].
 *
 * The coroutine [run] function contains the main behaviour. It may suspend freely (for delays, pathfinding, waiting
 * on actions, etc) while still respecting the game-thread execution model.
 *
 * @param T The snapshot type used to persist this script's state (e.g., a data class or primitive), or `Void?` for
 * scripts that do not persist any data.
 * @property bot The owning bot instance that this script controls.
 * @author lare96
 */
abstract class BotScript<T>(val bot: Bot) {

    /**
     * Handler for simulated inbound messages to the bot.
     *
     * This provides access to the same input pipeline that a real client would use (e.g., button clicks,
     * movement actions).
     */
    protected val input: BotInputMessageHandler = bot.botClient.input

    /**
     * Handler for simulated outbound messages from the bot.
     *
     * This allows scripts to enqueue packets that would normally be sent by a real client (e.g., walking,
     * interaction, casting spells).
     */
    protected val output: BotOutputMessageHandler = bot.botClient.output

    /**
     * High-level action handler for common bot operations.
     *
     * Encapsulates reusable behaviour (banking, walking, skilling, etc.) so that multiple scripts can share logic
     * rather than reimplementing the same flows.
     */
    protected val handler: BotActionHandler = bot.actionHandler

    /**
     * The coroutine job tracking this script's execution state.
     *
     * This is:
     * - `null` when the script has never been started or has been fully stopped.
     * - `isActive` while [run] is executing or suspended.
     * - `isCompleted && !isCancelled` when the script finished normally.
     * -`isCancelled` when the script was interrupted via [stop].
     */
    private var progress: Job? = null

    /**
     * The main coroutine body for this script.
     *
     * This function is always executed on the game thread through [GameCoroutineScope] and must be written with that
     * assumption.
     */
    abstract suspend fun run()

    /**
     * Produces a snapshot of this script's persistent state.
     *
     * The returned value will be wrapped in a [BotScriptSnapshot] and stored alongside the owning character data,
     * allowing this script to be restored after a server restart or relog.
     *
     * Implementations should:
     * - Capture only the minimal data needed to resume behaviour (e.g., target position, mode, counters).
     * - Avoid holding references to live game objects that cannot safely be serialized.
     *
     * @return A serializable snapshot representing the current state of this script.
     */
    abstract fun snapshot(): T

    /**
     * Starts this script by launching its [run] coroutine.
     *
     * If the script is already running and has not been cancelled, this method is a no-op and returns `false`.
     * Otherwise, a new [Job] is created in [GameCoroutineScope] and stored in [progress].
     *
     * @return `true` if the script was successfully started, `false` if it was already running.
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
     * Stops this script by cancelling its active coroutine.
     *
     * If the script is currently running, its [Job] is cancelled, [progress] is cleared, and this method
     * returns `true`. If the script is idle or already completed/cancelled, this method is a no-op and
     * returns `false`.
     *
     * @return `true` if the script was running and has now been stopped, `false` otherwise.
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
     * Returns whether this script has completed normally.
     *
     * A script is considered “finished” if:
     * - [progress] is not `null`.
     * - The underlying job is `isCompleted`.
     * - The job was not cancelled (`!isCancelled`).
     *
     * @return `true` if the script completed without interruption, `false` otherwise.
     */
    fun isFinished(): Boolean {
        return progress != null && progress!!.isCompleted && !progress!!.isCancelled
    }

    /**
     * Returns whether this script is currently running.
     *
     * <p>
     * A script is considered “running” if [progress] is non-null and its job is `isActive`.
     * </p>
     *
     * @return `true` if the script coroutine is active, `false` otherwise.
     */
    fun isRunning(): Boolean {
        return progress != null && progress!!.isActive
    }

    /**
     * Returns whether this script was interrupted.
     *
     * <p>
     * A script is considered “interrupted” if [progress] is non-null and its job has been cancelled
     * (either explicitly via [stop] or by some other cancellation).
     * </p>
     *
     * @return `true` if the script was cancelled, `false` otherwise.
     */
    fun isInterrupted(): Boolean {
        return progress != null && progress!!.isCancelled
    }

    /**
     * Returns whether this script is idle and has not yet started.
     *
     * <p>
     * A script is “idle” when no [Job] has been assigned to [progress] (i.e., [start] has never been
     * successfully invoked, or [stop] has cleared it).
     * </p>
     *
     * @return `true` if the script has no active or completed job, `false` otherwise.
     */
    fun isIdle(): Boolean {
        return progress == null
    }
}
