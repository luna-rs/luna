package game.bot.scripts.scams

import api.bot.social.BotConversations
import api.bot.social.BotTradeScams
import api.bot.zone.SubZone
import api.predef.world
import engine.bot.speech.BotScamSpeechPool
import game.bot.scripts.IdleBotScript
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.speech.BotSpeech

/**
 * A bounded social visit advertising either money doubling or armour trimming to available bots only.
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
        val victim = world.locator.findViewablePlayers(bot) {
            it is Bot && it !== bot && it.isWithinDistance(bot, 8) && BotConversations.available(it)
        }.filterIsInstance<Bot>().shuffled().firstOrNull { BotTradeScams.approach(bot, it, kind) }
        if (victim == null && !BotTradeScams.engaged(bot)) {
            BotScamSpeechPool.take(bot, kind.advert)?.let { bot.speechStack.push(BotSpeech(it, 4)) }
        }
    }
}
