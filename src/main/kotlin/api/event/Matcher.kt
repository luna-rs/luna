package api.event

import api.predef.*
import com.google.common.collect.ArrayListMultimap
import io.luna.game.event.Event
import io.luna.game.event.EventMatcher
import io.luna.game.event.EventMatcherListener
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.CommandEvent
import io.luna.game.event.impl.GroundItemClickEvent.GroundItemSecondClickEvent
import io.luna.game.event.impl.ItemClickEvent
import io.luna.game.event.impl.ItemClickEvent.*
import io.luna.game.event.impl.NpcClickEvent
import io.luna.game.event.impl.NpcClickEvent.*
import io.luna.game.event.impl.ObjectClickEvent
import io.luna.game.event.impl.ObjectClickEvent.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.event.impl.UseItemEvent.*
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

        /**
         * Determines if the [eventType] has a dedicated matcher.
         */
        fun <E : Event> has(eventType: KClass<E>) = ALL.containsKey(eventType)

        init {
            // Map all matchers to their matching event types.
            ALL = listOf(CommandMatcher,
                         ButtonMatcher,
                         ItemOnItemMatcher,
                         ItemOnObjectMatcher,
                         ItemOnNpcMatcher,
                         ItemOnPlayerMatcher,
                         ItemOnGroundItemMatcher,
                         GroundItemSecondClickMatcher,
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

            // Add all the matcher's listeners.
            ALL.values.forEach { it.addListener() }

            // Small optimization, put all keys with only one listener into a different map.
            on(ServerLaunchEvent::class) {
                var singularCount = 0
                var multiCount = 0
                for (matcher in ALL.values) {
                    val result = matcher.optimize()
                    singularCount += result.first
                    multiCount += result.second
                }
            }
        }
    }

    /**
     * The map of event keys to action function instances. Will be used to match arguments.
     */
    private val actions = HashMap<K, EventMatcherListener<E>>(128)

    /**
     * The map of event keys to multiple action function instances. Will be used to match arguments.
     */
    private val multiActions = ArrayListMultimap.create<K, EventMatcherListener<E>>(128, 16)

    /**
     * Computes a lookup key from the event instance.
     */
    abstract fun key(msg: E): K

    /**
     * A set containing all keys in this matcher.
     */
    fun keys(): Set<K> {
        val singular = actions.keys
        val multi = multiActions.asMap().keys
        val keys = HashSet<K>(singular.size + multi.size)
        keys.addAll(singular)
        keys.addAll(multi)
        return keys
    }

    /**
     * Adds or replaces an optimized listener key -> value pair.
     */
    operator fun set(key: K, value: E.() -> Unit) {
        val matcherListener = EventMatcherListener(value)
        multiActions.put(key, matcherListener)
        scriptMatchers += matcherListener
    }

    /**
     * Adds an event listener for this matcher to the backing pipeline set.
     */
    private fun addListener() {
        val type = eventType.java
        val pipeline = pipelines.get(type)
        pipeline.setMatcher(EventMatcher(this::match))
    }

    /**
     * Matches [msg] to an event listener within this matcher.
     */
    private fun match(msg: E): Boolean {
        val actionKey = key(msg)

        // Try a singular action.
        val action = actions[actionKey]
        if (action != null) {
            action.apply(msg)
            return true
        } else {
            // If not found, try multiple actions.
            val multiAction = multiActions[actionKey]
            if (multiAction.size > 0) {
                multiAction.forEach { it.apply(msg) }
                return true
            }
            return false
        }
    }

    /**
     * Optimizes this matcher by putting all keys with only one listener into a different map.
     */
    private fun optimize(): Pair<Int, Int> {
        val iterator = multiActions.asMap().entries.iterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            val actionList = next.value
            if (actionList.size == 1) {
                actions[next.key] = actionList.first()
                iterator.remove()
            }
        }
        return actions.size to multiActions.size()
    }

    /**
     * A base [Matcher] for [NpcClickEvent]s.
     */
    class NpcMatcher<E : NpcClickEvent>(matchClass: KClass<E>) : Matcher<E, Int>(matchClass) {
        override fun key(msg: E) = msg.targetNpc.id
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
     * A singleton [Matcher] instance for [GroundItemSecondClickEvent]s.
     */
    object GroundItemSecondClickMatcher : Matcher<GroundItemSecondClickEvent, Int>(GroundItemSecondClickEvent::class) {
        override fun key(msg: GroundItemSecondClickEvent): Int = msg.groundItem.id
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
    object CommandMatcher : Matcher<CommandEvent, CommandKey>(CommandEvent::class) {
        // Note: The rights value is ignored, the real key is 'msg.name'.
        override fun key(msg: CommandEvent) = CommandKey(msg.name, msg.plr.rights)
    }

    /**
     * A singleton [Matcher] instance for [ItemOnItemEvent]s.
     */
    object ItemOnItemMatcher : Matcher<ItemOnItemEvent, Pair<Int, Int>>(ItemOnItemEvent::class) {
        override fun key(msg: ItemOnItemEvent) = Pair(msg.usedItemId, msg.targetItemId)
    }

    /**
     * A singleton [Matcher] instance for [ItemOnObjectEvent]s.
     */
    object ItemOnObjectMatcher : Matcher<ItemOnObjectEvent, Pair<Int, Int>>(ItemOnObjectEvent::class) {
        override fun key(msg: ItemOnObjectEvent) = Pair(msg.usedItemId, msg.objectId)
    }

    /**
     * A singleton [Matcher] instance for [ItemOnNpcEvent]s.
     */
    object ItemOnNpcMatcher : Matcher<ItemOnNpcEvent, Pair<Int, Int>>(ItemOnNpcEvent::class) {
        override fun key(msg: ItemOnNpcEvent) = Pair(msg.usedItemId, msg.targetNpc.id)
    }

    /**
     * A singleton [Matcher] instance for [ItemOnPlayerEvent]s.
     */
    object ItemOnPlayerMatcher : Matcher<ItemOnPlayerEvent, Int>(ItemOnPlayerEvent::class) {
        override fun key(msg: ItemOnPlayerEvent) = msg.usedItemId
    }

    /**
     * A singleton [Matcher] instance for [ItemOnGroundItemEvent]s.
     */
    object ItemOnGroundItemMatcher : Matcher<ItemOnGroundItemEvent, Pair<Int, Int>>(ItemOnGroundItemEvent::class) {
        override fun key(msg: ItemOnGroundItemEvent) = Pair(msg.usedItemId, msg.groundItem.id)
    }
}