package game.bot.scripts.scams

import api.bot.action.PkLoadoutPreparation
import api.bot.social.BotConversations
import api.bot.social.BotSocialInteractions
import api.bot.social.BotWildernessLures
import api.bot.zone.SubZone
import api.predef.world
import engine.bot.speech.BotScamSpeechPool
import engine.bot.speech.BotScamSpeechPool.ScamSpeech
import game.bot.scripts.IdleBotScript
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.speech.BotSpeech

/**
 * Recruits available bots at Edgeville for an authored Wilderness lure. A solo actor prepares an owned, budgeted PK
 * outfit before advertising; accomplice mode recruits a compatible prepared patrol without changing either identity.
 * The idle visit owns travel and expiry, and the shared social protocol owns the bounded following journey.
 *
 * @author lare96
 */
class PkScamBotScript(bot: Bot, deadline: Long,
                      private val useAccomplice: Boolean = !BotWildernessLures.canAmbush(bot) || (0..1).random() == 0) :
    IdleBotScript(bot, SubZone.EDGEVILLE_MARKETPLACE, deadline) {

    override suspend fun init(resumed: Boolean): Boolean {
        if (!bot.preferences.isScammer) return true
        if (!useAccomplice && (!BotWildernessLures.canAmbush(bot) || !PkLoadoutPreparation(bot, handler).prepare())) return true
        return super.init(resumed)
    }

    override fun snapshot(): IdleData? = null

    override fun considerConversation() {
        val victim = world.locator.findViewablePlayers(bot) {
            it is Bot && it !== bot && it.isWithinDistance(bot, 8) && BotConversations.available(it)
        }.filterIsInstance<Bot>().shuffled().firstOrNull { BotWildernessLures.approach(bot, it, useAccomplice) }
        if (victim == null && !BotSocialInteractions.engaged(bot)) {
            BotScamSpeechPool.take(bot, ScamSpeech.LURE_ADVERT)?.let { bot.speechStack.push(BotSpeech(it, 4)) }
        }
    }
}
