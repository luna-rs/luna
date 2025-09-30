package api.bot.zone

import api.bot.action.BotActionHandler
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot

/**
 * An interface that allows subclasses to force a [Bot] to travel in different ways.
 *
 * @author lare96
 */
interface TravelStrategy {

    /**
     * Determines if [bot] can travel to [dest].
     */
    fun canTravel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean

    /**
     * A blocking function that forces [bot] to travel to [dest].
     */
    suspend fun travel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean
}