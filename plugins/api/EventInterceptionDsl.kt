package api

import io.luna.game.event.Event
import io.luna.game.event.EventArguments
import io.luna.game.event.EventListener
import kotlin.reflect.KClass

/**
 * The main event interception function. Forwards to [TestEvent].
 */
fun <E : Event> on(eventClass: KClass<E>) = TestEvent(eventClass)

/**
 * Adds a new [EventListener] to the backing list, registering the script's listener.
 */
private fun <E : Event> addListener(eventClass: KClass<E>, args: EventArguments, action: (E) -> Unit) {
    val eventListener = EventListener(eventClass.java, args, action)
    scriptListeners.add(eventListener)
}

/**
 * The first builder. Determines if and how the event will be tested (condition, args, run).
 */
class TestEvent<E : Event>(private val eventClass: KClass<E>) {

    /**
     * Use a condition to test the event. Forwards to [ConditionTest].
     */
    fun condition(cond: (E) -> Boolean) = ConditionTest(eventClass, cond)

    /**
     * Use arguments to test the event. Forwards to [ArgsTest].
     */
    fun args(vararg matchArgs: Any) = ArgsTest(eventClass, matchArgs)

    /**
     * No test, just run!
     */
    fun run(action: (E) -> Unit) = addListener(eventClass, EventArguments.NO_ARGS, action)
}

/**
 * A final builder. Tests if the event satisfies an arbitrary condition.
 */
class ConditionTest<E : Event>(private val eventClass: KClass<E>, private val cond: (E) -> Boolean) {

    /**
     * Test condition, and run if satisfied!
     */
    fun run(action: (E) -> Unit) = addListener(eventClass, EventArguments.NO_ARGS) {
        if (cond(it)) {
            action(it)
        }
    }
}

/**
 * A final builder. Tests if the event satisfies [Event.matches], and terminates the event if it does.
 */
class ArgsTest<E : Event>(private val eventClass: KClass<E>, private val args: Array<out Any>) {

    /**
     * Test arguments, and run if satisfied!
     */
    fun run(action: (E) -> Unit) {
        val eventArguments = EventArguments(args)
        scriptListeners.add(EventListener(eventClass.java, eventArguments, action))
    }
}