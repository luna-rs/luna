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
     * Use a condition to test the event. This function **will terminate** the event if the condition is
     * satisfied. Forwards to [InterceptCondition].
     */
    fun filter(cond: E.() -> Boolean) = InterceptCondition(eventType, cond, true)

    /**
     * Use a condition to test the event. Forwards to [InterceptCondition].
     */
    fun condition(cond: E.() -> Boolean) = InterceptCondition(eventType, cond, false)

    /**
     * Use a [Matcher] to test the event on [args]. This function only works for event types that have a dedicated
     * matcher. Forwards to [InterceptMatcher].
     */
    fun <K> match(args: Iterable<K>): InterceptMatcher<E, K> {
        if (!Matcher.has(eventType)) {
            throw IllegalStateException("There is no dedicated matcher for event type: ${eventType.simpleName}. Use 'filter' instead of 'match'.")
        }
        return InterceptMatcher(eventType, args)
    }

    /**
     * Use a [Matcher] to test the event on [arg0], [arg1], and [argOther]. Forwards to the other [InterceptBy.match]
     * function under the hood.
     */
    fun <K> match(arg0: K, arg1: K, vararg argOther: K): InterceptMatcher<E, K> {
        val args = HashSet<K>(2 + argOther.size)
        args += arg0
        args += arg1
        args += argOther
        return match(args)
    }
}