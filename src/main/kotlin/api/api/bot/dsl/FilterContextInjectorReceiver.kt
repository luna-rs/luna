package api.bot.dsl

import api.predef.*
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.injection.BotContextInjector
import io.luna.game.model.mob.bot.injection.InjectableEvent
import kotlin.reflect.KClass

/**
 * A DSL receiver used to finalize the definition of a bot event injector by attaching an action block that
 * executes when the filter condition passes.
 *
 * Instances of this class are created by [TypeContextInjectorReceiver.filter]. When the `then` function is called,
 * a new [BotContextInjector] is built and registered with the [scriptInjectors] list.
 *
 * @param E The [InjectableEvent] subtype being handled.
 * @property type The [KClass] reference of the event type.
 * @property filter The filter predicate that determines whether this injector
 *                  should handle the event.
 *
 * @author lare96
 */
class FilterContextInjectorReceiver<E : InjectableEvent>(private val type: KClass<E>,
                                                         private val filter: BotInjectorWrapper<E>.() -> Boolean) {

    /**
     * Defines the action to perform when the filter predicate succeeds.
     *
     * This function registers a new [BotContextInjector] that listens for the specified event type and applies the
     * configured filter and action blocks. The newly created injector is stored in the global [scriptInjectors] list.
     *
     * @param action The block of logic to execute when the filter passes.
     */
    @Suppress("UNCHECKED_CAST")
    fun then(action: BotInjectorWrapper<E>.() -> Unit) {
        scriptInjectors.add(object : BotContextInjector {
            override fun onEvent(bot: Bot, event: InjectableEvent): Boolean {
                if (type.java.isAssignableFrom(event.javaClass)) {
                    val wrapper = BotInjectorWrapper(bot, event as E)
                    if (filter(wrapper)) {
                        action(wrapper)
                        return true
                    }
                }
                return false
            }
        })
    }
}
