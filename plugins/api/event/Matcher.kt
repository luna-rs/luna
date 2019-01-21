package api.event

import api.predef.*
import io.luna.game.event.Event
import io.luna.game.event.EventListener
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.CommandEvent
import io.luna.game.event.impl.ItemClickEvent
import io.luna.game.event.impl.ItemClickEvent.*
import io.luna.game.event.impl.NpcClickEvent
import io.luna.game.event.impl.NpcClickEvent.*
import io.luna.game.event.impl.ObjectClickEvent
import io.luna.game.event.impl.ObjectClickEvent.*
import io.luna.game.plugin.Script
import java.nio.file.Paths
import kotlin.reflect.KClass

/**
 * A model that will be used to match arbitrary arguments against events of type [E]. Matchers obtain a [K]ey
 * from an event message and use the key to lookup the appropriate action to run.
 *
 * This provides a performance increase when there are a large amount of event listeners in a pipeline. For
 * more information, see [issue #68](https://github.com/luna-rs/luna/issues/68).
 *
 * @author lare96
 */
abstract class Matcher<E : Event, K>(private val eventType: KClass<E>) {

    companion object {

        /**
         * Mappings of all matchers. The event type is the key.
         */
        val ALL: Map<KClass<out Event>, Matcher<*, *>>

        /**
         * The dummy script instance to use for [EventListener]s.
         */
        private val SCRIPT = Script("matcherScript", Paths.get(""), "")

        /**
         * Retrieves a [Matcher] from the backing map that is matching on events with [E].
         */
        @Suppress("UNCHECKED_CAST")
        inline fun <reified E : Event, K> get(): Matcher<E, K> {
            return get(E::class)
        }

        /**
         * Retrieves a [Matcher] from the backing map that is matching on events with [eventType].
         */
        @Suppress("UNCHECKED_CAST")
        fun <E : Event, K> get(eventType: KClass<E>): Matcher<E, K> {
            val matcher = ALL[eventType]
            return when (matcher) {
                null -> throw NoSuchElementException("Matcher for event type $eventType was not found.")
                else -> matcher as Matcher<E, K>
            }
        }

        init {
            // Map all matchers to their matching event types.
            ALL = listOf(CommandMatcher,
                         ButtonMatcher,
                         NpcMatcher(NpcFirstClickEvent::class),
                         NpcMatcher(NpcSecondClickEvent::class),
                         NpcMatcher(NpcThirdClickEvent::class),
                         NpcMatcher(NpcFourthClickEvent::class),
                         NpcMatcher(NpcFifthClickEvent::class),
                         ItemMatcher(ItemFirstClickEvent::class),
                         ItemMatcher(ItemSecondClickEvent::class),
                         ItemMatcher(ItemThirdClickEvent::class),
                         ItemMatcher(ItemFourthClickEvent::class),
                         ItemMatcher(ItemFifthClickEvent::class),
                         ObjectMatcher(ObjectFirstClickEvent::class),
                         ObjectMatcher(ObjectSecondClickEvent::class),
                         ObjectMatcher(ObjectThirdClickEvent::class))
                .associateBy { it.eventType }

            // Add all of the matcher's listeners.
            ALL.values.forEach { it.addListener() }
        }
    }

    /**
     * The map of event keys to action function instances. Will be used to match arguments.
     */
    private val actions = mutableMapOf<K, E.() -> Unit>()

    /**
     * Computes a lookup key from the event instance.
     */
    abstract fun key(msg: E): K

    /**
     * A set containing all keys in this matcher.
     */
    fun keys(): Set<K> = actions.keys

    /**
     * Adds or replaces an optimized listener key -> value pair.
     */
    operator fun set(key: K, value: E.() -> Unit) {
        val previous = actions.put(key, value)
        if (previous != null) {
            logger.warn("Key <$key> has overridden a $eventType listener.")
        }
    }

    /**
     * Adds an event listener for this matcher to the backing pipeline set.
     */
    private fun addListener() {
        val type = eventType.java
        val eventListener = EventListener<E>(type) {
            val actionKey = key(it)
            val action = actions[actionKey]
            if (action != null) {
                action(it)
                it.terminate()
            }
        }
        eventListener.script = SCRIPT
        pipelines.get(type).setMatcher(eventListener)
    }

    /**
     * A base [Matcher] for [NpcClickEvent]s.
     */
    class NpcMatcher<E : NpcClickEvent>(matchClass: KClass<E>) : Matcher<E, Int>(matchClass) {
        override fun key(msg: E) = msg.npc.id
    }

    /**
     * A base [Matcher] for [ItemClickEvent]s.
     */
    class ItemMatcher<E : ItemClickEvent>(matchClass: KClass<E>) : Matcher<E, Int>(matchClass) {
        override fun key(msg: E) = msg.id
    }

    /**
     * A base [Matcher] for [ObjectClickEvent]s.
     */
    class ObjectMatcher<E : ObjectClickEvent>(matchClass: KClass<E>) : Matcher<E, Int>(matchClass) {
        override fun key(msg: E) = msg.id
    }

    /**
     * A singleton [Matcher] instance for [ButtonClickEvent]s.
     */
    object ButtonMatcher : Matcher<ButtonClickEvent, Int>(ButtonClickEvent::class) {
        override fun key(msg: ButtonClickEvent) = msg.id
    }

    /**
     * A singleton [Matcher] instance for [CommandEvent]s.
     */
    object CommandMatcher : Matcher<CommandEvent, String>(CommandEvent::class) {
        override fun key(msg: CommandEvent) = msg.name!!
    }
}

