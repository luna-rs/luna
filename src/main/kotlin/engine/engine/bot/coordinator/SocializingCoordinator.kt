package engine.bot.coordinator

import api.bot.script.BotScript
import api.bot.action.trading.BotMarketplace
import api.bot.zone.SubZone
import api.predef.*
import engine.bot.coordinator.skill.MagicScriptFactory
import game.bot.scripts.PartyRoomDonations
import game.bot.scripts.PartyRoomEntertainment
import game.bot.scripts.IdleBotScript
import game.bot.scripts.scams.TradeScamBotScript
import game.bot.scripts.scams.PkScamBotScript
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import kotlin.time.Duration.Companion.minutes

/**
 * Considers a voluntary Party Room donation before a bounded idle visit at home or a configured marketplace.
 * Alchemy can fill the visit using the same social gathering area and conversation support as ordinary casters.
 * Scammer-template bots sometimes use the same visit lifecycle to pitch a bot-only trust trade instead.
 *
 * @author lare96
 */
object SocializingCoordinator : ProgressiveCoordinator() {
    override val activity get() = BotActivity.SOCIALIZING
    override fun firstScript(bot: Bot): BotScript? = PartyRoomDonations.select(bot) ?: PartyRoomEntertainment.select(bot)
    override fun nextScript(bot: Bot): BotScript {
        val destination = if (randBoolean()) SubZone.HOME else BotMarketplace.select(bot).activityZone
        val minutes = if (bot.preferences.likesActivity(BotActivity.SOCIALIZING)) rand(10, 30) else rand(5, 15)
        val deadline = System.currentTimeMillis() + minutes * 60_000L
        if (bot.preferences.isScammer && rand(0.30 + bot.personality.confidence * 0.5)) {
            return if (rand(0.25)) PkScamBotScript(bot, deadline) else TradeScamBotScript(bot, destination, deadline)
        }
        if (rand(0.30)) {
            MagicScriptFactory.getAlchemyScript(bot, bot.magic.staticLevel, duration = minutes.minutes)?.let { return it }
        }
        return IdleBotScript(bot, destination, deadline)
    }
}
