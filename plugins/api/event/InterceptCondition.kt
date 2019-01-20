package api.event

import api.predef.*
import io.luna.game.event.Event
import io.luna.game.event.EventListener
import kotlin.reflect.KClass

/**
 * A model that adds an event listener from the [InterceptBy.filter] function. If the event satisfies
 * [condition], an action will be ran and the event will be terminated.
 *
 * @author lare96
 */
class InterceptCondition<E : Event>(private val eventType: KClass<E>,
                                    private val condition: E.() -> Boolean,
                                    private val terminate: Boolean) {

    /**
     * Test condition, and run if satisfied!
     */
    fun then(action: E.() -> Unit) {
        val wrappedAction: E.() -> Unit = {
            if (condition(this)) {
                action(this)
                if (terminate) {
                    terminate()
                }
            }
        }
        scriptListeners.add(EventListener(eventType.java, wrappedAction))
    }
}