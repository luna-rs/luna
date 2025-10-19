package api.bot.action

import api.bot.SuspendableCondition
import api.bot.SuspendableFuture
import api.bot.SuspendableFuture.SuspendableFutureFailed
import io.luna.game.model.Locatable
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
        val timeout = bot.position.computeLongestDistance(target) * 5L;
        val cond = SuspendableCondition { bot.isWithinDistance(target, radius) }
        bot.walking.walk(target)
        return cond.submit(timeout)
    }

    /**
     * An action that forces the [Bot] to walk to [target]. The returned future will unsuspend once the bot has reached
     * the target, stops moving, or a timeout occurs.
     *
     * @param target The target to walk to.
     */
    fun walkUntilReached(target: Locatable): SuspendableFuture {
        bot.resetInteractingWith()
        bot.resetInteractionTask()
        bot.log("Walking until $target is reached.")
        val location = target.location()
        val timeout = bot.position.computeLongestDistance(location) * 5L;
        val cond = SuspendableCondition { bot.walking.isEmpty && bot.position.isViewable(target.location()) }
        return if (bot.walking.walkUntilReached(target))
            cond.submit(timeout) else SuspendableFutureFailed
    }
}