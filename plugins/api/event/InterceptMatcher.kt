package api.event

import io.luna.game.event.Event
import kotlin.reflect.KClass

/**
 * A model that adds event listeners from the [InterceptBy.match] function. If a matcher is found for
 * [eventType], then [args] are mapped to an action function.
 *
 * @author lare96
 */
class InterceptMatcher<E : Event, K>(private val eventType: KClass<E>, private val args: Iterable<K>) {

    /**
     * If a matcher exists for [eventType], map [args] to [action].
     */
    fun then(action: E.() -> Unit) {
        // Use the matcher, instead of registering (n) listeners.
        val matcher: Matcher<E, K> = Matcher.get(eventType)

        args.forEach { matcher[it] = action }
    }
}