package api.bot.zone

import api.bot.action.BotActionHandler
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot

/**
 * Defines how a bot should travel to a destination.
 *
 * A travel strategy can represent any movement method, such as walking directly, using teleports, crossing obstacles,
 * interacting with objects, or combining several actions into one route.
 *
 * @author lare96
 */
interface TravelStrategy {

    /**
     * Checks whether the given bot can use this strategy to reach the destination.
     *
     * This should only perform availability checks, such as required items, unlocked routes, distance limits, or whether
     * the destination is supported by this strategy. It should not actually move the bot.
     *
     * @param bot The bot attempting to travel.
     * @param handler The action handler used to inspect or perform bot interactions.
     * @param dest The destination position the bot wants to reach.
     * @return `true` if this strategy can be used to travel to [dest], otherwise `false`.
     */
    fun canTravel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean

    /**
     * Attempts to move the given bot to the destination using this strategy.
     *
     * This is a blocking suspend function and should only return after the strategy succeeds, fails, or determines that
     * it can no longer continue. Implementations may walk, interact with world objects, use inventory items, teleport,
     * or perform any other required actions.
     *
     * @param bot The bot that should travel.
     * @param handler The action handler used to perform movement and interactions.
     * @param dest The destination position the bot should travel to.
     * @return `true` if the bot successfully travelled using this strategy, otherwise `false`.
     */
    suspend fun travel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean
}