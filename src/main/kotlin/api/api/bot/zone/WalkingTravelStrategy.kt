package api.bot.zone

import api.bot.Suspendable
import api.bot.Suspendable.delay
import api.bot.Suspendable.naturalDelay
import api.bot.SuspendableCondition
import api.bot.action.BotActionHandler
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.movement.NavigationResult
import kotlinx.coroutines.future.await
import kotlin.time.Duration.Companion.seconds

/**
 * A [TravelStrategy] implementation that forces a [Bot] to walk to its destination.
 *
 * @author lare96
 */
object WalkingTravelStrategy : TravelStrategy {

    // TODO@0.5.0 Calculate path first, determine if valid (in canTravel), path later.

    override fun canTravel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean = true

    override suspend fun travel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean {
        if (bot.position.isWithinDistance(dest, Position.VIEWING_DISTANCE / 2)) {
            return true
        }
        handler.widgets.clickRunning(true)
        bot.naturalDelay()
        val future = bot.navigator.navigate(dest, true)
        while (!future.isDone) {
            if (bot.isViewableFrom(dest)) {
                return true
            }
            delay(1.seconds, 3.seconds)
        }
        return future.await() == NavigationResult.REACHED
    }
}