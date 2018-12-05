package api.event

import api.predef.*
import io.luna.game.event.Event
import kotlin.reflect.KClass

/**
 * A model that contains functions for intercepting events in different ways. Forwarded from [on].
 *
 * @author lare96
 */
class InterceptBy<E : Event>(private val eventType: KClass<E>) {

    /**
     * Use a condition to test the event. Terminates the event is the condition is satisfied. Forwards
     * to [InterceptCondition].
     */
    fun condition(cond: (E) -> Boolean) = InterceptCondition(eventType, cond, true)

    /**
     * Use a condition to test the event. Forwards to [InterceptCondition].
     */
    fun filter(cond: (E) -> Boolean) = InterceptCondition(eventType, cond, false)

    /**
     * Use a [Matcher] to test the event. This function only works for event types that have a dedicated
     * matcher. Forwards to [InterceptMatcher].
     */
    fun <K> match(vararg args: K) = InterceptMatcher(eventType, args)
}