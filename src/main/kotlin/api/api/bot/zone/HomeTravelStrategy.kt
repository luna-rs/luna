package api.bot.zone

import api.bot.Suspendable.waitFor
import api.bot.action.BotActionHandler
import api.bot.zone.SubZone.HOME
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot

/**
 * A [TravelStrategy] implementation that forces a [Bot] to teleport home, and then walk to its destination.
 *
 * @author lare96
 */
object HomeTravelStrategy : TravelStrategy {
    override fun canTravel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean = true
    override suspend fun travel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean {
        if (bot.subZone != HOME) {
            bot.output.sendCommand("home")
            if (!waitFor { bot.subZone == HOME }) {
                bot.log("Home teleport failed or timed out.")
                return false
            }
        }
        // Continue walking from home area.
        return WalkingTravelStrategy.travel(bot, handler, dest)
    }
}