package api.bot.social

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.script.BotScript
import api.bot.script.BotScript.ActionSafety
import api.predef.rand
import engine.trade.tradingWith
import engine.widget.settings.Emote
import game.bot.scripts.IdleBotScript
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.mob.bot.Bot

/**
 * Occasional native control-tab emotes for settled idle visitors, using Luna's existing supported emote buttons.
 * Eligibility is checked immediately before sending a packet; urgent actions and normal work keep priority.
 *
 * @author lare96
 */
object BotIdleEmotes {
    const val CHANCE = 0.45
    val DECISION_MILLIS = 45_000L..120_000L
    val EMOTES = listOf(Emote.YES, Emote.NO, Emote.THINK, Emote.BOW, Emote.LAUGH, Emote.CHEER,
        Emote.WAVE, Emote.BECKON, Emote.CLAP, Emote.DANCE, Emote.YAWN, Emote.SHRUG)

    /**
     * The idle activity that queued this native button, retained only until delivery or the next emote.
     *
     * @author lare96
     */
    private data class PendingEmote(val owner: BotScript, val button: Int)

    private var Bot.pendingEmote: PendingEmote? by Attr.nullableObj(PendingEmote::class)

    /**
     * Requires a living, stationary visitor with no interface, reflex, negotiation or queued gameplay action.
     */
    fun ready(bot: Bot): Boolean = bot.state == EntityState.ACTIVE && ActionSafety.NON_COMBAT.allows(bot) &&
        bot.combat.target == null && bot.reflex.activeReflex == null && bot.walking.isEmpty &&
        !bot.navigator.isActive && !bot.overlays.hasWindow() && bot.tradingWith == -1 &&
        bot.actions.size(ActionType.WEAK) == 0

    /**
     * Requires the currently running activity to be a settled idle visit.
     */
    fun canDeliver(bot: Bot): Boolean {
        val script = bot.scriptStack.current() as? IdleBotScript ?: return false
        return script.isRunning() && script.canSocialize() && ready(bot)
    }

    /**
     * Consumes exactly one queued emote and rechecks its original owner and urgent state at native delivery.
     */
    fun accept(bot: Bot, button: Int): Boolean {
        val pending = bot.pendingEmote ?: return false
        bot.pendingEmote = null
        return pending.button == button && bot.scriptStack.current() === pending.owner && canDeliver(bot)
    }

    /**
     * Chooses a different supported emote when a due decision succeeds, without delaying the idle coroutine.
     */
    fun perform(bot: Bot, previousId: Int): Emote? {
        if (!canDeliver(bot) || !rand(CHANCE)) return null
        val emote = EMOTES.filter { it.id != previousId }.random()
        bot.pendingEmote = PendingEmote(bot.scriptStack.current(), emote.button)
        if (bot.output.clickButton(emote.button)) return emote
        bot.pendingEmote = null
        return null
    }
}
