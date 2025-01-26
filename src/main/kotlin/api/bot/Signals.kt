package api.bot

import io.luna.game.model.Entity
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.BotScript

/**
 * A collection of commonly used signals for [SuspendableFuture].
 */
object Signals {

    /**
     * A signal to determine if [bot] is interacting with [target].
     */
    fun Bot.interacting(target: Entity): () -> Boolean =
        { interactingWith.filter { target == it }.isPresent }

    /**
     * A signal to determine if [bot] is within [distance] of [target].
     */
    fun Bot.within(target: Entity, distance: Int): () -> Boolean =
        within(target.position, distance)

    /**
     * A signal to determine if [bot] is within [distance] of [position].
     */
    fun Bot.within(position: Position, distance: Int): () -> Boolean =
        { position.isWithinDistance(position, distance) }
}