package api.bot.zone

import api.bot.action.BotActionHandler
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot

/**
 * A [TravelStrategy] implementation that forces a [Bot] to walk to its destination with the web-walker.
 *
 * The strategies that teleport before walking (such as [TeleportTravelStrategy]) finish their trips with this one.
 *
 * @author lare96
 */
object WalkingTravelStrategy : TravelStrategy {

    override suspend fun canTravel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean = true

    override suspend fun travel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean {
        if (bot.isViewableFrom(dest)) {
            return true
        }
        return handler.webWalkTo(dest, BotActionHandler.WEB_WALK_AREA_RADIUS, BotActionHandler.WEB_WALK_ATTEMPTS) ||
                bot.isViewableFrom(dest)
    }
}
