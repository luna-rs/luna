package api.bot.zone

import api.bot.Suspendable.delay
import api.bot.Suspendable.naturalDelay
import api.bot.action.BotActionHandler
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration.Companion.seconds

/**
 * A [TravelStrategy] implementation that forces a [Bot] to walk to its destination.
 *
 * @author lare96
 */
object WalkingTravelStrategy : TravelStrategy {

    override fun canTravel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean = true

    override suspend fun travel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean {
        if (bot.position.isWithinDistance(dest, Position.VIEWING_DISTANCE / 2)) {
            return true
        }
        handler.widgets.clickRunning(true)
        bot.naturalDelay()
        return handler.movement.walkUntilReached(dest).await()
    }
}