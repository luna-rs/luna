package engine.bot.coordinator

import api.bot.script.BotScript
import api.bot.action.trading.BotMarketplace
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.PartyRoomDonations
import game.bot.scripts.IdleBotScript
import game.bot.scripts.scams.TradeScamBotScript
import game.bot.scripts.scams.PkScamBotScript
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity

/**
 * Considers a voluntary Party Room donation before a bounded idle visit at home or a configured marketplace.
 * The visit exposes social availability after arrival and supports paired conversations without interrupting work.
 * Scammer-template bots sometimes use the same visit lifecycle to pitch a bot-only trust trade instead.
 *
 * @author lare96
 */
object SocializingCoordinator : ProgressiveCoordinator() {
    override val activity get() = BotActivity.SOCIALIZING
    override fun firstScript(bot: Bot): BotScript? = PartyRoomDonations.select(bot)
    override fun nextScript(bot: Bot): BotScript {
        val destination = if (randBoolean()) SubZone.HOME else BotMarketplace.select(bot).activityZone
        val minutes = if (bot.preferences.likesActivity(BotActivity.SOCIALIZING)) rand(10, 30) else rand(5, 15)
        val deadline = System.currentTimeMillis() + minutes * 60_000L
        return if (bot.preferences.isScammer && rand(0.25 + bot.personality.confidence * 0.5))
            if (rand(0.25)) PkScamBotScript(bot, deadline) else TradeScamBotScript(bot, destination, deadline)
        else IdleBotScript(bot, destination, deadline)
    }
}
