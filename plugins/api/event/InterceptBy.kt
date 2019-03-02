package api.event

import api.predef.*
import io.luna.game.event.Event
import javax.script.ScriptException
import kotlin.reflect.KClass

/**
 * A model that contains functions for intercepting events in different ways. Forwarded from [on].
 *
 * @author lare96
 */
class InterceptBy<E : Event>(private val eventType: KClass<E>) {

    /**
     * Filtering function that executes the event listener if the condition is `true`. Forwards to
     * [InterceptFilter].
     */
    fun filter(cond: E.() -> Boolean) = InterceptFilter(eventType, cond, true)


    /**
     * Filtering function that executes the event listener if the condition is `false`. Forwards to
     * [InterceptFilter].
     */
    fun filterNot(cond: E.() -> Boolean) = InterceptFilter(eventType, cond, false)

    /**
     * Use a [Matcher] to test the event on [args]. This function only works for event types that have a dedicated
     * matcher. Forwards to [InterceptMatcher].
     */
    fun <K> match(args: Iterable<K>): InterceptMatcher<E, K> {
        if (!Matcher.has(eventType)) {
            throw ScriptException("There is no dedicated matcher for event type: ${eventType.simpleName}. Use 'filter' instead of 'match'.")
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