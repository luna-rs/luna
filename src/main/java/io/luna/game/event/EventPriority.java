package io.luna.game.event;

import io.luna.game.event.impl.DropItemEvent;
import io.luna.game.event.impl.GroundItemClickEvent.PickupItemEvent;

/**
 * Represents the priority level assigned to event listeners within the Luna event system.
 * <p>
 * Each listener registered for an event type is assigned one of three priority levels:
 * {@link #LOW}, {@link #NORMAL}, or {@link #HIGH}. The priority determines the order in
 * which listeners are executed during event dispatch.
 * </p>
 *
 * <p>
 * Priorities are typically assigned automatically depending on how the listener was
 * declared. For example:
 * <ul>
 *     <li>{@code LOW} – Default for unfiltered event listeners</li>
 *     <li>{@code NORMAL} – Default for filtered listeners and matchers</li>
 *     <li>{@code HIGH} – Reserved for a single “default” implementation per event type</li>
 * </ul>
 * </p>
 *
 * @author lare96
 */
public enum EventPriority {

    /**
     * The default priority for standard unfiltered listeners.
     * <p>
     * Used when registering basic event handlers such as:
     * <pre>
     * on(ChatEvent::class) {
     *     ...
     * }
     * </pre>
     */
    LOW,

    /**
     * The default priority for filtered listeners and matchers.
     * <p>
     * Filtered listeners and automatic matchers are assigned this level by default. The listeners
     * are ran before the matchers.
     * <pre>
     * on(ChatEvent::class).filter { plr is Bot }.then {
     *     ...
     * }
     * </pre>
     */
    NORMAL,

    /**
     * The highest priority, reserved for “default functionality” of the event.
     * <p>
     * Only one high-priority listener may exist per event type. These are executed
     * before all others and define the base behavior of the event.
     * </p>
     * <p>
     * Example:
     * <ul>
     *     <li>For {@link DropItemEvent}, the high-priority listener performs the actual drop</li>
     *     <li>For {@link PickupItemEvent}, it performs the actual pickup</li>
     * </ul>
     * </p>
     */
    HIGH
}
