package api.bot

import io.luna.game.model.Entity
import io.luna.game.model.mob.bot.Bot

/**
 * A collection of commonly used signals for [SuspendableFuture].
 */
object Signals {

    /**
     * A signal to determine if [bot] is interacting with [target].
     */
    fun interacting(bot: Bot, target: Entity): () -> Boolean =
        { bot.interactingWith.filter { target == it }.isPresent }

    /**
     * A signal to determine if [bot] is within [distance] of [target].
     */
    fun within(bot: Bot, target: Entity, distance: Int): () -> Boolean =
        { bot.position.isWithinDistance(target.position, distance) }
}