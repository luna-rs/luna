package api.bot.action

import api.bot.SuspendableCondition
import api.bot.SuspendableFuture
import io.luna.game.model.Entity
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot

/**
 * A [BotActionHandler] implementation for movement related actions.
 */
class BotMovementActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * An action that forces the [Bot] to walk to [target]. The returned future will unsuspend once the bot is [radius]
     * squares from the target position, the bot stops moving, or a timeout occurs.
     *
     * @param target The position to walk to.
     */
    fun walk(target: Position, radius: Int = 0): SuspendableFuture {
        // Assuming each square takes 5 seconds to walk just to be safe.
        val timeoutSeconds = bot.position.computeLongestDistance(target) * 5L;
        val suspendableCond = SuspendableCondition({ bot.isWithinDistance(target, radius) }, timeoutSeconds)
        bot.walking.walk(target)
        return suspendableCond.submit()
    }

    /**
     * An action that forces the [Bot] to walk to [target]. The returned future will unsuspend once the bot has reached
     * the target, stops moving, or a timeout occurs.
     *
     * @param target The target to walk to.
     */
    fun walkUntilReached(target: Entity): SuspendableFuture {
        val collisionManager = bot.world.collisionManager
        val timeoutSeconds = bot.computeLongestDistance(target) * 5L;
        val suspendableCond =
            SuspendableCondition({ bot.walking.isEmpty || collisionManager.reached(bot, target) },
                                 timeoutSeconds)
        bot.walking.walkUntilReached(target)
        return suspendableCond.submit()
    }
}