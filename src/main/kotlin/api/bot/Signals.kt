package api.bot

import io.luna.game.model.Entity
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot

/**
 * A collection of commonly used signals for [SuspendableFuture].
 */
object Signals {

    // TODO Make this an instanced class in BotScript with Bot as an arg. reduces redundancy

    /**
     * A signal to determine if [bot] is interacting with [target].
     */
    fun interacting(bot: Bot, target: Entity): () -> Boolean =
        { bot.interactingWith.filter { target == it }.isPresent }

    /**
     * A signal to determine if [bot] is within [distance] of [target].
     */
    fun within(bot: Bot, target: Entity, distance: Int): () -> Boolean =
        within(bot, target.position, distance)

    /**
     * A signal to determine if [bot] is within [distance] of [position].
     */
    fun within(bot: Bot, position: Position, distance: Int): () -> Boolean =
        { bot.position.isWithinDistance(position, distance) }
}