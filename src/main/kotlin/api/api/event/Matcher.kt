package api.event

import api.predef.*
import com.google.common.collect.ArrayListMultimap
import io.luna.game.event.Event
import io.luna.game.event.EventMatcher
import io.luna.game.event.EventMatcherListener
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.CommandEvent
import io.luna.game.event.impl.GroundItemClickEvent.GroundItemSecondClickEvent
import io.luna.game.event.impl.InteractableEvent
import io.luna.game.event.impl.ItemClickEvent
import io.luna.game.event.impl.ItemClickEvent.*
import io.luna.game.event.impl.NpcClickEvent
import io.luna.game.event.impl.NpcClickEvent.*
import io.luna.game.event.impl.ObjectClickEvent
import io.luna.game.event.impl.ObjectClickEvent.*
import io.luna.game.event.impl.UseItemEvent.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionActionListener
import kotlin.reflect.KClass

/**
 * A matcher that routes events of type [E] by deriving a lookup key of type [K].
 *
 * Matchers provide an optimized alternative to scanning every listener in an event pipeline. Each incoming event is
 * transformed into a key via [key], and that key is then used to find the matching listeners directly.
 *
 * Matchers may also produce [InteractionActionListener] instances for interaction-driven events so listener execution
 * can be deferred until movement and reach requirements are satisfied.
 *
 * @author lare96
 */
abstract class Matcher<E : Event, K : Any>(private val eventType: KClass<E>) {

    companion object {

        /**
         * All registered matchers keyed by their event type.
         */
        val ALL: Map<KClass<out Event>, Matcher<*, *>>

        /**
         * Returns the matcher responsible for events of type [E].
         *
         * @return The matcher for [E].
         * @throws NoSuchElementException If no matcher exists for [E].
         */
        inline fun <reified E : Event, K : Any> get(): Matcher<E, K> {
            return get(E::class)
        }

        /**
         * Returns the matcher responsible for [eventType].
         *
         * @param eventType The event type to look up.
         * @return The matcher for [eventType].
         * @throws NoSuchElementException If no matcher exists for [eventType].
         */
        @Suppress("UNCHECKED_CAST")
        fun <E : Event, K : Any> get(eventType: KClass<E>): Matcher<E, K> {
            return when (val matcher = ALL[eventType]) {
                null -> throw NoSuchElementException("Matcher for event type $eventType was not found.")
                else -> matcher as Matcher<E, K>
            }
        }

        /**
         * Returns whether [eventType] has a dedicated matcher.
         *
         * @param eventType The event type to check.
         * @return `true` if a matcher exists for [eventType].
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
        }
    }

    /**
     * The mapping of computed event keys to registered matcher listeners.
     */
    private val actions = ArrayListMultimap.create<K, EventMatcherListener<E>>()

    /**
     * Computes the lookup key for [msg].
     *
     * @param msg The event being routed.
     * @return The key used to look up listeners for [msg].
     */
    abstract fun key(msg: E): K

    /**
     * Returns all keys currently registered in this matcher.
     *
     * @return The registered matcher keys.
     */
    fun keys(): Set<K> {
        return HashSet(actions.keys())
    }

    /**
     * Registers an optimized listener for [key].
     *
     * The listener will only be considered for events whose computed key matches [key].
     *
     * @param key The matcher key.
     * @param value The listener to execute when [key] is matched.
     * @param interaction The interaction policy supplier used when this listener participates in an
     *        interaction-based event flow.
     */
    fun set(key: K, value: E.() -> Unit, interaction: InteractionPolicySupplier) {
        val matcherListener = EventMatcherListener(value, interaction)
        actions.put(key, matcherListener)
        scriptMatchers += matcherListener
    }

    /**
     * Returns whether this matcher has at least one listener for [msg].
     *
     * @param msg The event to check.
     * @return `true` if a listener exists for [msg].
     */
    fun has(msg: E) = actions.containsKey(key(msg))

    /**
     * Registers this matcher with the backing event pipeline for [eventType].
     *
     * The pipeline receives an [EventMatcher] that delegates matching, existence checks, and interaction listener
     * creation back to this matcher.
     */
    private fun addListener() {
        val type = eventType.java
        val pipeline = pipelines.get(type)
        pipeline.setMatcher(EventMatcher(this::match, this::has, this::interactions))
    }

    /**
     * Attempts to match [msg] against listeners registered under its computed key.
     *
     * If one or more listeners are found, they are executed in registration order.
     *
     * @param msg The event to match.
     * @return `true` if at least one listener was matched and invoked, otherwise {@code false}.
     */
    private fun match(msg: E): Boolean {
        val listeners = actions[key(msg)]
        if (listeners.isEmpty()) {
            return false
        }
        for (it in listeners) {
            it.apply(msg)
        }
        return true
    }

    /**
     * Creates interaction listeners for [msg] using the listeners registered under its computed key.
     *
     * Each matched listener is wrapped as an [InteractionActionListener] so it can be executed later by the
     * interaction system once the player has satisfied the required reach policy.
     *
     * @param plr The player attempting the interaction.
     * @param msg The event being converted into interaction listeners.
     * @return The interaction listeners created for [msg], or an empty list if none were matched.
     */
    fun interactions(plr: Player, msg: E): List<InteractionActionListener> {
        val listeners = actions[key(msg)]
        val list = ArrayList<InteractionActionListener>()
        if (listeners.isEmpty()) {
            return emptyList()
        }
        for (it in listeners) {
            if (msg is InteractableEvent) {
                val target = msg.target()
                list.add(InteractionActionListener(it.interaction.apply(plr, target)) { it.apply(msg) })
            }
        }
        return list
    }

    /**
     * A base [Matcher] for [NpcClickEvent] types.
     *
     * NPC click events are matched by target NPC id.
     */
    class NpcMatcher<E : NpcClickEvent>(matchClass: KClass<E>) : Matcher<E, Int>(matchClass) {
        override fun key(msg: E) = msg.targetNpc.id
    }

    /**
     * A base [Matcher] for [ItemClickEvent] types.
     *
     * Item click events are matched by item id.
     */
    class ItemMatcher<E : ItemClickEvent>(matchClass: KClass<E>) : Matcher<E, Int>(matchClass) {
        override fun key(msg: E) = msg.id
    }

    /**
     * A base [Matcher] for [ObjectClickEvent] types.
     *
     * Object click events are matched by object id.
     */
    class ObjectMatcher<E : ObjectClickEvent>(matchClass: KClass<E>) : Matcher<E, Int>(matchClass) {
        override fun key(msg: E) = msg.id
    }

    /**
     * A singleton matcher for [GroundItemSecondClickEvent]s.
     *
     * Events are matched by ground item id.
     */
    object GroundItemSecondClickMatcher : Matcher<GroundItemSecondClickEvent, Int>(GroundItemSecondClickEvent::class) {
        override fun key(msg: GroundItemSecondClickEvent): Int = msg.groundItem.id
    }

    /**
     * A singleton matcher for [ButtonClickEvent]s.
     *
     * Events are matched by button id.
     */
    object ButtonMatcher : Matcher<ButtonClickEvent, Int>(ButtonClickEvent::class) {
        override fun key(msg: ButtonClickEvent) = msg.id
    }

    /**
     * A singleton matcher for [CommandEvent]s.
     *
     * Commands are matched by [CommandKey]. The rights value is included in the key type, although command name is
     * the primary lookup component.
     */
    object CommandMatcher : Matcher<CommandEvent, CommandKey>(CommandEvent::class) {
        override fun key(msg: CommandEvent) = CommandKey(msg.name, msg.plr.rights)
    }

    /**
     * A singleton matcher for [ItemOnItemEvent]s.
     *
     * Events are matched by the pair of used item id and target item id.
     */
    object ItemOnItemMatcher : Matcher<ItemOnItemEvent, Pair<Int, Int>>(ItemOnItemEvent::class) {
        override fun key(msg: ItemOnItemEvent) = Pair(msg.usedItemId, msg.targetItemId)
    }

    /**
     * A singleton matcher for [ItemOnObjectEvent]s.
     *
     * Events are matched by the pair of used item id and object id.
     */
    object ItemOnObjectMatcher : Matcher<ItemOnObjectEvent, Pair<Int, Int>>(ItemOnObjectEvent::class) {
        override fun key(msg: ItemOnObjectEvent) = Pair(msg.usedItemId, msg.objectId)
    }

    /**
     * A singleton matcher for [ItemOnNpcEvent]s.
     *
     * Events are matched by the pair of used item id and target NPC id.
     */
    object ItemOnNpcMatcher : Matcher<ItemOnNpcEvent, Pair<Int, Int>>(ItemOnNpcEvent::class) {
        override fun key(msg: ItemOnNpcEvent) = Pair(msg.usedItemId, msg.targetNpc.id)
    }

    /**
     * A singleton matcher for [ItemOnPlayerEvent]s.
     *
     * Events are matched by used item id.
     */
    object ItemOnPlayerMatcher : Matcher<ItemOnPlayerEvent, Int>(ItemOnPlayerEvent::class) {
        override fun key(msg: ItemOnPlayerEvent) = msg.usedItemId
    }

    /**
     * A singleton matcher for [ItemOnGroundItemEvent]s.
     *
     * Events are matched by the pair of used item id and target ground item id.
     */
    object ItemOnGroundItemMatcher : Matcher<ItemOnGroundItemEvent, Pair<Int, Int>>(ItemOnGroundItemEvent::class) {
        override fun key(msg: ItemOnGroundItemEvent) = Pair(msg.usedItemId, msg.groundItem.id)
    }
}