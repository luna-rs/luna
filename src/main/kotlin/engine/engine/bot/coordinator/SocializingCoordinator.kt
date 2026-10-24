package engine.bot.coordinator

import api.bot.script.BotScript
import api.bot.action.trading.BotMarketplace
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.PartyRoomDonations
import game.bot.scripts.IdleBotScript
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity

/**
 * Considers a voluntary Party Room donation before a bounded idle visit at home or a configured marketplace.
 * The visit exposes social availability after arrival and supports paired conversations without interrupting work.
 *
 * @author lare96
 */
object SocializingCoordinator : ProgressiveCoordinator() {
    override val activity get() = BotActivity.SOCIALIZING
    override fun firstScript(bot: Bot): BotScript? = PartyRoomDonations.select(bot)
    override fun nextScript(bot: Bot): BotScript {
        val destination = if (randBoolean()) SubZone.HOME else BotMarketplace.select(bot).activityZone
        val minutes = if (bot.preferences.likesActivity(BotActivity.SOCIALIZING)) rand(10, 30) else rand(5, 15)
        return IdleBotScript(bot, destination, System.currentTimeMillis() + minutes * 60_000L)
    }
}
