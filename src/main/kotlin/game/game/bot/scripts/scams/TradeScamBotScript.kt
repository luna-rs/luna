package game.bot.scripts.scams

import api.bot.isRealBot

import api.bot.social.BotConversations
import api.bot.social.BotTradeScams
import api.bot.social.BotSocialInteractions
import api.bot.social.PlayerTradeScams
import api.bot.zone.SubZone
import api.predef.world
import engine.bot.speech.BotScamSpeechPool
import game.bot.scripts.IdleBotScript
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.speech.BotSpeech

/**
 * A bounded social visit pitching bot trust trades or a rare misleading sale to a nearby player.
 * Travel, idle timing, interruption and partner conversation delivery use the ordinary idle script lifecycle.
 * Each visit retains its chosen pitch; trust trades remain transient and are never replayed after login.
 *
 * @author lare96
 */
class TradeScamBotScript(bot: Bot, destination: SubZone, deadline: Long,
                         private val kind: BotTradeScams.Kind = BotTradeScams.Kind.entries.random()) :
    IdleBotScript(bot, destination, deadline) {

    override fun snapshot(): IdleData? = null

    override fun considerConversation() {
        if (!bot.preferences.isScammer) return
        val player = world.locator.findViewablePlayers(bot) {
            !it.isRealBot() && it.isWithinDistance(bot, 8)
        }.randomOrNull()
        if (player != null && PlayerTradeScams.approach(bot, player)) return
        val victim = world.locator.findViewablePlayers(bot) {
            it.isRealBot() && it !== bot && it.isWithinDistance(bot, 8) && BotConversations.available(it)
        }.filterIsInstance<Bot>().shuffled().firstOrNull { BotTradeScams.approach(bot, it, kind) }
        if (victim == null && !BotSocialInteractions.engaged(bot)) {
            BotScamSpeechPool.take(bot, kind.advert)?.let { bot.speechStack.push(BotSpeech(it, 4)) }
        }
    }
}
