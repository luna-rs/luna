package api.bot.action

import api.bot.SuspendableCondition
import io.luna.game.model.Locatable
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.movement.BotMovementStack
import kotlinx.coroutines.future.await

/**
 * A [BotActionHandler] implementation for movement related actions.
 */
class BotMovementActionHandler(private val bot: Bot) {

    /**
     * An action that forces the [Bot] to walk to [target]. The function will unsuspend once the bot is [radius]
     * squares from the target position, the bot stops moving, or a timeout occurs.
     *
     * @param target The position to walk to.
     */
    suspend fun BotMovementStack.walk(target: Locatable, radius: Int = 0) {
        bot.resetInteractingWith()
        bot.resetInteractionTask()

        bot.log("Walking until $target is reached.")
        bot.movementStack.addPath(target).await()

        val location = target.location()
        val timeout = bot.position.computeLongestDistance(location) * 5L;
        SuspendableCondition { bot.walking.isEmpty && bot.position.isViewable(target.location()) }.submit(timeout)
    }
}