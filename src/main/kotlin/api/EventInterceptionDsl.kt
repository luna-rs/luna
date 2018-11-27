package api

import io.luna.game.event.*
import kotlin.reflect.KClass


fun <E : Event> on(eventClass: KClass<E>) = EventInterceptionBuilder(eventClass)

/* First builder. Determines if we're using a condition, arguments, or nothing to test the event. */
class EventInterceptionBuilder<E : Event>(private val eventClass: KClass<E>) {

    /* Use a condition to test the event before running. */
    fun condition(cond: (E) -> Boolean) = ConditionRunner(eventClass, cond)

    /* Use arguments to test the event before running. */
    fun args(vararg matchArgs: Any) = ArgsRunner(eventClass, matchArgs)

    /* No test, just run! */
    fun run(action: (E) -> Unit) = {
        val eventListener = EventListener(eventClass.java, EventArguments.NO_ARGS, action)
        scriptListeners.add(eventListener)
    }
}

class ConditionRunner<E : Event>(private val eventClass: KClass<E>, private val cond: (E) -> Boolean) {
    fun run(action: (E) -> Unit) {
        val eventListener = EventListener(eventClass.java, EventArguments.NO_ARGS, wrapListener(action))
        scriptListeners.add(eventListener)
    }

    private fun wrapListener(action: (E) -> Unit): (E) -> Unit = {
        if (cond.invoke(it)) {
            action.invoke(it)
            it.terminate()
        }
    }
}

class ArgsRunner<E : Event>(private val eventClass: KClass<E>, private val args: Array<out Any>) {
    fun run(action: (E) -> Unit) {
        val eventArguments = EventArguments(args)
        scriptListeners.add(EventListener(eventClass.java, eventArguments, action))
    }
}