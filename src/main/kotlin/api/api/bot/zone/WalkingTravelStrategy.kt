package api.bot.zone

import api.attr.Attr
import api.bot.Suspendable.delay
import api.bot.Suspendable.naturalDelay
import api.bot.action.BotActionHandler
import api.bot.zone.WalkingTravelStrategy.path
import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.path.route.RoutePathfinder
import kotlinx.coroutines.future.await
import kotlin.time.Duration.Companion.seconds

/**
 * A [TravelStrategy] implementation that forces a [Bot] to walk to its destination.
 *
 * @author lare96
 */
object WalkingTravelStrategy : TravelStrategy {

    /**
     * An attribute representing the path a bot will walk during a travel strategy.
     */
    private var Bot.path by Attr.obj { java.util.ArrayDeque<Position>() }

    override suspend fun canTravel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean {
        return setPath(bot, dest)
    }

    override suspend fun travel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean {
        if (bot.isViewableFrom(dest)) {
            return true
        }
        if (bot.path.isEmpty() && !setPath(bot, dest)) {
            return false
        }
        handler.widgets.clickRunning(true)
        bot.naturalDelay()
        bot.walking.replacePath(bot.path)
        while (!bot.walking.isEmpty) {
            if (bot.isViewableFrom(dest)) {
                return true
            }
            delay(1.seconds, 3.seconds)
        }
        return bot.isViewableFrom(dest)
    }

    /**
     * Generates a path to [dest] and stores it in the [path] attribute.
     *
     * @return `true` if a path was successfully generated.
     */
    private suspend fun setPath(bot: Bot, dest: Position): Boolean {
        val computedPath =
            bot.navigator.findPath(bot.position, dest, RoutePathfinder.forBot(bot), true).await()
        if (computedPath?.peekLast()?.isViewableFrom(dest) == true) {
            bot.path = java.util.ArrayDeque(computedPath)
            return true
        }
        return false
    }
}