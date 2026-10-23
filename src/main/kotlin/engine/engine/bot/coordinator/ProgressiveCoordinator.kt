package engine.bot.coordinator

import api.bot.script.BotScript
import api.bot.script.BotScriptData
import com.google.gson.JsonObject
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator

/**
 * Runs an optional preliminary activity before selecting the coordinator's main activity.
 *
 * The continuation is queued independently, so a skipped, stopped or failed preliminary script still advances.
 * Pausing retains both stages; saved continuations select their main activity after restoration without repeating
 * the preliminary decision. Main activities are selected only after the preliminary script leaves the stack.
 *
 * @author lare96
 */
abstract class ProgressiveCoordinator : BotCoordinator {
    /**
     * The activity used to resolve this coordinator when restoring a pending continuation.
     * Implementations use a getter to avoid circular initialization of BotActivity singleton coordinators.
     */
    protected abstract val activity: BotActivity

    /**
     * Selects an optional preliminary script on each coordinator invocation.
     */
    protected abstract fun firstScript(bot: Bot): BotScript?

    /**
     * Selects the main activity using the bot's state after the preliminary stage.
     */
    protected abstract fun nextScript(bot: Bot): BotScript?

    final override fun accept(bot: Bot) {
        try {
            firstScript(bot)?.let { bot.scriptStack.push(it) }
        } finally {
            bot.scriptStack.push(Continuation(bot, activity, this))
        }
    }

    /**
     * Persists the deferred dispatch independently of the preliminary script's outcome.
     *
     * @param bot The bot whose script stack will receive the main activity.
     * @property activity The activity used to resolve the coordinator after restoration.
     * @property source The current coordinator instance, or null when restored from saved data.
     * @property dispatched Whether main activity selection has already been attempted.
     *
     * @author lare96
     */
    class Continuation(
        bot: Bot,
        private val activity: BotActivity,
        private val source: ProgressiveCoordinator? = null,
        private var dispatched: Boolean = false
    ) : BotScript(bot) {
        /**
         * Stores the originating activity rather than serializing coordinator instances.
         *
         * @author lare96
         */
        class ContinuationData : BotScriptData() {
            /**
             * The originating activity used to resolve the coordinator after restoration.
             */
            var activity = BotActivity.SOCIALIZING
            /**
             * Whether main activity selection already occurred and must not be repeated.
             */
            var dispatched = false
            override fun load(data: JsonObject) {
                activity = BotActivity.valueOf(data.get("activity").asString)
                dispatched = data.get("dispatched")?.asBoolean ?: false
            }
            override fun save(data: JsonObject) {
                data.addProperty("activity", activity.name)
                data.addProperty("dispatched", dispatched)
            }
        }

        /**
         * Restores a deferred activity selection for [bot] from its saved [data].
         */
        constructor(bot: Bot, data: ContinuationData) : this(bot, data.activity, dispatched = data.dispatched)

        override suspend fun run(): Boolean {
            if (dispatched) return true
            val coordinator = source ?: activity.coordinator as? ProgressiveCoordinator ?: return true
            dispatched = true
            coordinator.nextScript(bot)?.let { bot.scriptStack.softPushHead(it) }
            return true
        }

        override fun snapshot() = ContinuationData().also {
            it.activity = activity
            it.dispatched = dispatched
        }
    }
}
