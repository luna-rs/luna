package game.bot.scripts.skills

import api.bot.Suspendable.delay
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.SlayerScriptFactory
import game.skill.slayer.ActiveSlayerTask
import game.skill.slayer.Slayer.activeSlayerTask
import game.skill.slayer.SlayerMaster
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.OptionDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Requests a native Slayer assignment, then queues the existing assignment-bound NPC combat script.
 * Successful task completion returns to an eligible, mapped master within a bounded session.
 * A task that cannot be fought,
 * or a combat session making no progress, ends the visit without changing the assignment or streak.
 * The continuation and native task persist independently, so relogging never creates a fresh task.
 *
 * @property deadline Absolute session expiry, including queued combat and time offline.
 * @property previousTask Last task/master checkpoint used to detect a combat session with no progress.
 * @property previousRemaining Remaining kills before the preceding combat session.
 * @author lare96
 */
class SlayerBotScript private constructor(
    bot: Bot,
    private val deadline: Long,
    private var previousTask: String?,
    private val previousRemaining: Int
) : BotScript(bot) {
    /**
     * Creates a new bounded Slayer session; native assignments are reused whenever present.
     */
    constructor(bot: Bot, duration: Duration) : this(bot,
        System.currentTimeMillis() + duration.inWholeMilliseconds, null, -1)

    /**
     * Restores only dispatch state, leaving native assignment persistence authoritative.
     */
    constructor(bot: Bot, data: SlayerData) : this(bot, data.deadline, data.previousTask, data.previousRemaining) {
        dispatched = data.dispatched
    }

    /**
     * A saved dispatcher that already queued combat must not enqueue the same pair again on restoration.
     */
    private var dispatched = false

    /**
     * Whether this invocation opened the master's dialogue and therefore owns its cleanup.
     */
    private var ownsDialogue = false

    /**
     * Returns whether the preceding combat session made no progress on this same native task.
     */
    internal fun noProgress(task: ActiveSlayerTask): Boolean =
        previousTask == key(task) && task.remaining >= previousRemaining

    /**
     * Searches the supplied footprint on every native plane. Town arrivals can be far from a master,
     * and Duradel's upstairs spawn must remain discoverable from Shilo's ground-floor arrival.
     */
    internal fun findMaster(master: SlayerMaster, zone: SubZone): Npc? {
        val center = zone.area.centerPosition
        for (plane in Position.HEIGHT_LEVELS.lowerEndpoint() until Position.HEIGHT_LEVELS.upperEndpoint()) {
            val found = world.locator.findNpcs(Position(center.x, center.y, plane), zone.area.tileRadius, true) {
                it.id == master.id && it.position in zone.area && it.position.z == plane
            }.firstOrNull()
            if (found != null) return found
        }
        return null
    }

    override suspend fun run(): Boolean {
        if (dispatched || System.currentTimeMillis() >= deadline || !SlayerScriptFactory.canTrain(bot)) return true
        if (bot.activeSlayerTask == null) {
            previousTask = null
            if (!SlayerScriptFactory.hasLoadout(bot) || !requestAssignment()) return true
        }
        val task = bot.activeSlayerTask ?: return true
        if (noProgress(task)) {
            bot.log("Slayer combat made no progress; preserving ${task.task} and its streak.")
            return true
        }
        val remaining = (deadline - System.currentTimeMillis()).milliseconds
        if (remaining <= Duration.ZERO) return true
        val combat = SlayerScriptFactory.combatScript(bot, remaining)
        if (combat == null) {
            bot.log("No supported, supply-ready camp for ${task.task}; preserving assignment and streak.")
            return true
        }
        // softPushHead inserts immediately after us: queue continuation first, then combat before it.
        dispatched = true
        bot.scriptStack.softPushHead(SlayerBotScript(bot, deadline, key(task), task.remaining))
        bot.scriptStack.softPushHead(combat)
        return true
    }

    /**
     * Uses native Talk-to and whitelisted response text. Existing tasks are never cancelled or replaced.
     * Dialogue continuation remains with the normal reflex; missing NPCs or responses fail within a minute.
     */
    private suspend fun requestAssignment(): Boolean {
        if (bot.activeSlayerTask != null) return true
        val selected = SlayerScriptFactory.selectMaster(bot) ?: return false
        val zone = SlayerScriptFactory.masterZones.getValue(selected)
        if (!handler.webWalkTo(zone) || !awaitActionSafety()) return false
        if (bot.activeSlayerTask != null) return true
        if (selected !in SlayerScriptFactory.eligibleMasters(bot.combatLevel, bot.slayer.level)) return false
        val master = findMaster(selected, zone) ?: return false
        if (!bot.position.isViewable(master.position) && !handler.webWalkTo(master.position, radius = 2)) return false
        if (!awaitActionSafety()) return false
        if (bot.activeSlayerTask != null) return true
        ownsDialogue = true
        if (!handler.interactions.interact(1, master) {
                isActionSafe() && bot.activeSlayerTask == null && System.currentTimeMillis() < deadline
            }) return bot.activeSlayerTask != null
        val dialogueDeadline = minOf(deadline, System.currentTimeMillis() + 60_000)
        while (System.currentTimeMillis() < dialogueDeadline) {
            if (!isActionSafe()) return false
            if (selected !in SlayerScriptFactory.eligibleMasters(bot.combatLevel, bot.slayer.level)) return false
            val options = bot.overlays[OptionDialogue::class]
            if (options != null) {
                val response = assignmentResponses.firstOrNull { response ->
                    options.options.any { it.equals(response, ignoreCase = true) }
                } ?: return false
                if (!handler.widgets.clickDialogueOption(response)) return false
            }
            if (bot.activeSlayerTask != null) {
                // No task roll, cancellation or reward is performed by this script.
                bot.overlays.closeWindows()
                ownsDialogue = false
                return true
            }
            delay(1.seconds)
        }
        return false
    }

    override suspend fun finish() {
        if (ownsDialogue) {
            bot.overlays.closeWindows()
            ownsDialogue = false
        }
    }

    override fun snapshot() = SlayerData().also {
        it.deadline = deadline
        it.previousTask = previousTask
        it.previousRemaining = previousRemaining
        it.dispatched = dispatched
    }

    /**
     * Session dispatch checkpoint; NPC combat and native task state use their existing persistence.
     *
     * @author lare96
     */
    class SlayerData : BotScriptData() {
        /**
         * Session expiry used across master visits, combat and restoration.
         */
        var deadline: Long = 0
        /**
         * Previous native task type and assigning master, or null before the first combat session.
         */
        var previousTask: String? = null
        /**
         * Kill count at the preceding dispatch, used to bound repeated failed combat preparation.
         */
        var previousRemaining: Int = -1
        /**
         * Whether combat and its continuation were already queued before this snapshot.
         */
        var dispatched = false

        override fun load(data: JsonObject) {
            deadline = data.get("deadline").asLong
            previousTask = data.get("previousTask")?.takeUnless { it.isJsonNull }?.asString
            previousRemaining = data.get("previousRemaining")?.asInt ?: -1
            dispatched = data.get("dispatched")?.asBoolean ?: false
        }

        override fun save(data: JsonObject) {
            data.addProperty("deadline", deadline)
            previousTask?.let { data.addProperty("previousTask", it) }
            data.addProperty("previousRemaining", previousRemaining)
            data.addProperty("dispatched", dispatched)
        }
    }

    companion object {
        /**
         * Safe native responses; cancellation, streak reset and difficulty changes are excluded.
         */
        private val assignmentResponses = listOf(
            "I need another assignment.", "No that's okay, I'll take a task from you.", "Okay, great!")

        /**
         * Stable dispatch identity used only for progress comparison, never for native task credit.
         */
        private fun key(task: ActiveSlayerTask) = "${task.assignee.name}:${task.task.name}"
    }
}
