package api.bot.social

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.script.BotScript
import api.bot.script.BotScript.ActionSafety
import engine.trade.tradingWith
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.speech.BotSpeech

/**
 * Opt-in availability from the current script's actual work state, independent of coordinator or location guesses.
 * Implementations exclude travel, banking and active negotiations, but do not test conversation ownership.
 * Ordinary training must explicitly opt into conversation while working.
 *
 * @author lare96
 */
interface SocialBotScript {
    /**
     * Whether this script is currently waiting and can accept a social approach.
     */
    fun canSocialize(): Boolean

    /** Explicit opt-in for talking while safely carrying on ordinary training. */
    fun canTalkWhileWorking(): Boolean = false

    /**
     * Invalidates activity-specific stock assumptions after a native social exchange actually completes.
     */
    fun socialTradeCompleted() {}
}

/**
 * Claims two available bots for one bounded, alternating conversation through their normal speech stacks.
 * Claims are runtime-only: pauses, logout, movement, emergencies and script changes cancel both sides.
 * No trade decisions, item movements or scam success are inferred from conversation text.
 *
 * @author lare96
 */
object BotConversations {
    /**
     * The current paired interaction; never persisted or stored in a global player registry.
     */
    private var Bot.conversation: Conversation? by Attr.nullableObj(Conversation::class)

    /**
     * Rejects busy bots even when their current script normally permits social approaches. A working bot's own
     * script judges its work; clicking the next target or rock must not end the exchange.
     */
    private fun ready(bot: Bot, allowMovement: Boolean = false, allowWorking: Boolean = false): Boolean {
        val working = allowWorking && (bot.scriptStack.current() as? SocialBotScript)?.canTalkWhileWorking() == true
        return bot.state == EntityState.ACTIVE &&
            (if (working) bot.health > 0 && !bot.isLocked else ActionSafety.NON_COMBAT.allows(bot)) &&
            bot.reflex.activeReflex == null &&
            (working || bot.combat.target == null) &&
            (working || allowMovement || bot.walking.isEmpty && !bot.navigator.isActive) &&
            (working || bot.actions.size(ActionType.WEAK) == 0) && !bot.overlays.hasWindow() &&
            bot.tradingWith == -1 && !bot.speechStack.isAllDisabled
    }

    /**
     * Explicit opt-in from a running script, with invalid previous ownership released before reuse.
     */
    fun available(bot: Bot, allowWorking: Boolean = false): Boolean {
        if (BotSocialInteractions.engaged(bot) || engaged(bot)) return false
        val script = bot.scriptStack.current()
        return script is SocialBotScript && script.isRunning() &&
            (allowWorking || !script.canTalkWhileWorking()) &&
            ready(bot, allowWorking = allowWorking) && script.canSocialize()
    }

    /**
     * Tests an existing claim, releasing both participants immediately when its live conditions expire.
     */
    fun engaged(bot: Bot): Boolean {
        val current = bot.conversation ?: return false
        if (!current.valid()) current.close()
        return !current.ended
    }

    /**
     * Acquires both participants atomically on the game thread. Each line alternates between first and second,
     * unless [speakers] names the speaker of every line (0 = first, 1 = second).
     * A caller may retain the returned handle to distinguish full delivery from cancellation in later scenarios.
     */
    fun start(first: Bot, second: Bot, lines: List<String>,
              allowMovement: Boolean = false,
              clock: () -> Long = { System.nanoTime() / 1_000_000 },
              interaction: BotSocialInteractions.Session? = null,
              allowWorking: Boolean = false,
              delivered: (Bot) -> Unit = {},
              speakers: List<Int> = emptyList()): Conversation? {
        if (first === second || lines.size !in 2..24 || lines.any { it.isBlank() || it.length > 80 } ||
            speakers.isNotEmpty() && (speakers.size != lines.size || speakers.any { it !in 0..1 }) ||
            !availableTo(first, interaction, allowMovement, allowWorking) ||
            !availableTo(second, interaction, allowMovement, allowWorking) ||
            !near(first, second)) return null
        val session = Conversation(listOf(first, second), lines.toList(), allowMovement, clock, interaction,
                                   delivered = delivered, allowWorking = allowWorking,
                                   speakers = speakers.toList())
        if (!first.speechStack.reserveConversation(session) { session.valid() }) return null
        if (!second.speechStack.reserveConversation(session) { session.valid() }) {
            first.speechStack.releaseConversation(session)
            return null
        }
        first.conversation = session
        second.conversation = session
        return session
    }

    /** A contextual reply to a human or a short reaction, using the same reservation and delivery loop. */
    fun say(bot: Bot, text: String, valid: () -> Boolean = { true }, typingLimit: Int = 20,
            delivered: (Bot) -> Unit = {}): Conversation? {
        if (text.isBlank() || text.length > 80 || !available(bot, allowWorking = true) || !valid()) return null
        val session = Conversation(listOf(bot), listOf(text), false,
                                   { System.nanoTime() / 1_000_000 }, null, valid, delivered, typingLimit, true)
        if (!bot.speechStack.reserveConversation(session) { session.valid() }) return null
        bot.conversation = session
        session.tick()
        return session
    }

    /**
     * An existing owner may continue talking during its journey without reopening recruitment. It must still own
     * both bots, retain their scripts and meet native speech/safety conditions; unrelated callers cannot bypass a claim.
     */
    private fun availableTo(bot: Bot, interaction: BotSocialInteractions.Session?, allowMovement: Boolean,
                            allowWorking: Boolean): Boolean {
        if (interaction == null) return available(bot, allowWorking)
        val script = bot.scriptStack.current()
        return BotSocialInteractions.owns(bot, interaction) && interaction.valid() &&
            script is SocialBotScript && script.isRunning() && ready(bot, allowMovement, allowWorking)
    }

    /**
     * Advances a shared session from either participant's normal script cycle.
     */
    fun tick(bot: Bot) {
        bot.conversation?.tick()
    }

    /**
     * Cancels only this bot's paired interaction and its partner's matching reservation.
     */
    fun cancel(bot: Bot) {
        bot.conversation?.close()
    }

    /**
     * Both participants must remain within conversational earshot and neither may ignore the other.
     */
    private fun near(first: Bot, second: Bot): Boolean = first.isWithinDistance(second, 8) &&
        !first.ignores.contains(second.usernameHash) && !second.ignores.contains(first.usernameHash)

    /**
     * A two-speaker session with one pending turn and actual public-chat acknowledgement.
     * The normal background queue is retained, while short explicit typing delays bypass idle-filler timing.
     *
     * @author lare96
     */
    class Conversation internal constructor(private val bots: List<Bot>, private val lines: List<String>,
                                             private val allowMovement: Boolean,
                                             private val clock: () -> Long,
                                             private val interaction: BotSocialInteractions.Session?,
                                             private val contextValid: () -> Boolean = { true },
                                             private val delivered: (Bot) -> Unit = {},
                                             private val typingLimit: Int = 20,
                                             private val allowWorking: Boolean = false,
                                             private val speakers: List<Int> = emptyList()) {
        /**
         * Script identities captured at acquisition; a paused or replaced activity cannot keep talking.
         */
        private val scripts = bots.map { it.scriptStack.current() }
        private val deadline = clock() + 10 * 60_000
        private var turnDeadline = deadline
        private var index = 0
        private var pending: BotSpeech? = null

        /**
         * Whether every line was acknowledged before the session ended.
         */
        var completed = false
            private set

        /**
         * Whether reservations were released after completion, invalidation or timeout.
         */
        var ended = false
            private set

        /**
         * Rechecks both live activities, proximity and deadlines before queueing or sending any turn.
         */
        internal fun valid(): Boolean = !ended && clock() < minOf(deadline, turnDeadline) &&
            contextValid() && (bots.size == 1 || near(bots[0], bots[1])) && bots.indices.all {
                val script = bots[it].scriptStack.current()
                script === scripts[it] && script is SocialBotScript && script.isRunning() &&
                    ready(bots[it], allowMovement, allowWorking) && (if (interaction == null) script.canSocialize()
                    else BotSocialInteractions.owns(bots[it], interaction) && interaction.valid())
            }

        /**
         * Advances only after actual delivery. A missing acknowledgement never produces the partner's reply.
         */
        fun tick() {
            if (!valid() || bots.any { !it.speechStack.ownsConversation(this) }) {
                close()
                return
            }
            if (pending != null) {
                if (pending?.isDelivered != true) return
                delivered(bots[if (speakers.isEmpty()) index % bots.size else speakers[index]])
                index++
                pending = null
            }
            if (index == lines.size) {
                completed = true
                close()
                return
            }
            val speaker = bots[if (speakers.isEmpty()) index % bots.size else speakers[index]]
            val ticks = (3 + lines[index].length / 6 + (1 - speaker.personality.social) * 6 + (0..3).random())
                .toInt().coerceIn(4, typingLimit.coerceIn(4, 20))
            val speech = BotSpeech(lines[index], ticks)
            if (!speaker.speechStack.speakConversation(this, speech)) {
                close()
                return
            }
            pending = speech
            turnDeadline = clock() + 30_000
        }

        /**
         * Releases only this session's reservations, leaving external mute flags and other speech intact.
         */
        fun close() {
            if (ended) return
            ended = true
            bots.forEach {
                if (it.conversation === this) it.conversation = null
                it.speechStack.releaseConversation(this)
            }
        }
    }
}
