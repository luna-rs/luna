package api.bot.dsl

import io.luna.game.model.mob.bot.injection.InjectableEvent
import kotlin.reflect.KClass

/**
 * A DSL entry point for creating a bot context injector that reacts to a specific [InjectableEvent] type.
 *
 * This receiver is typically used as the first stage in a chain, where you define which event type the bot injector
 * should listen for, before optionally applying additional filters or actions.
 *
 * Internally, it delegates to [FilterContextInjectorReceiver] to build the final
 * [io.luna.game.model.mob.bot.injection.BotContextInjector] instance when the `then` clause is invoked.
 *
 * @param E The [InjectableEvent] subtype this receiver listens for.
 * @property type The [KClass] reference representing the event type.
 *
 * @author lare96
 */
class TypeContextInjectorReceiver<E : InjectableEvent>(private val type: KClass<E>) {

    /**
     * Adds a filter predicate to this injector definition.
     * <p>
     * The predicate determines whether the action block will run for a given event.
     * Returns a [FilterContextInjectorReceiver] which can be chained with a `then`
     * block to specify the behavior when the filter passes.
     *
     * @param predicate The condition used to decide whether the bot should respond
     *                  to the event.
     * @return A [FilterContextInjectorReceiver] instance for further configuration.
     */
    fun filter(predicate: BotInjectorWrapper<E>.() -> Boolean) =
        FilterContextInjectorReceiver(type, predicate)
}
