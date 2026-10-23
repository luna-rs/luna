package engine.bot.coordinator

import api.bot.script.BotScript
import api.bot.script.DynamicBotScript
import api.bot.zone.Zone
import api.predef.*
import game.bot.scripts.PartyRoomDonations
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity

/**
 * Considers a voluntary Party Room donation before selecting a short social visit.
 *
 * @author lare96
 */
object SocializingCoordinator : ProgressiveCoordinator() {
    override val activity get() = BotActivity.SOCIALIZING
    override fun firstScript(bot: Bot): BotScript? = PartyRoomDonations.select(bot)
    override fun nextScript(bot: Bot): BotScript = object : DynamicBotScript(bot) {
        override suspend fun run(): Boolean {
            repeat(rand(1, 5)) { handler.travelTo(Zone.SAFE_ZONES.random()) }
            return true
        }
    }
}
