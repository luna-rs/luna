package api.bot.action

import api.bot.Signals.within
import api.bot.SuspendableCondition
import api.bot.SuspendableFuture
import io.luna.game.model.Entity
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.bot.Bot

/**
 * A [BotActionHandler] implementation for movement related actions.
 */
class BotMovementActionHandler(bot: Bot) : BotActionHandler(bot) {

    /**
     * An action that forces the [Bot] to walk to [target]. The returned future will unsuspend once the bot is [radius]
     * squares from the target.
     *
     * @param target The position to walk to.
     */
    fun walk(target: Position, radius: Int): SuspendableFuture {
        // Assuming each square takes 5 seconds to walk just to be safe.
        val timeoutSeconds = bot.position.computeLongestDistance(target) * 5L;
        val suspendableCond = SuspendableCondition(within(target, radius), timeoutSeconds)
        bot.walking.walk(target);
        return suspendableCond.submit()
    }

    /**
     * An action that forces the [Bot] to walk to [target]. The returned future will unsuspend once the bot is
     * `target.size()` squares from the target.
     *
     * @param target The position to walk to.
     */
    fun walk(target: Entity): SuspendableFuture {
        return walk(target.position, target.size())
    }

    /**
     * An action that forces the [Bot] to walk behind [target]. The returned future will unsuspend once the
     * bot is within `target.size()` squares of the target.
     *
     * @param target The mob to walk behind.
     */
    fun walkBehind(target: Mob): SuspendableFuture {
        // Assuming each square takes 5 seconds to walk just to be safe.
        val timeoutSeconds = bot.position.computeLongestDistance(target.position) * 5L;
        val suspendableCond = SuspendableCondition(within(target, target.size()), timeoutSeconds)
        bot.walking.walkBehind(target)
        return suspendableCond.submit()
    }
}