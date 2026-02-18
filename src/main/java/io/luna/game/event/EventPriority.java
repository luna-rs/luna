package io.luna.game.event;

import io.luna.game.event.impl.DropItemEvent;
import io.luna.game.event.impl.GroundItemClickEvent.PickupItemEvent;

/**
 * Priority levels for listeners in Luna's event system.
 * <p>
 * In practice:
 * <ul>
 *   <li>{@link #HIGH} is reserved for the single "default behavior" implementation of an event.</li>
 *   <li>{@link #NORMAL} is used for common filtered listeners and middleware-style logic.</li>
 *   <li>{@link #LOW} is used for broad/unfiltered listeners and "after everything" hooks.</li>
 * </ul>
 *
 * @author lare96
 */
public enum EventPriority {

    /**
     * Lowest priority.
     * <p>
     * Executed last. Commonly used for broad/unfiltered handlers and "post-processing" hooks.
     */
    LOW,

    /**
     * Standard priority.
     * <p>
     * Executed after {@link #HIGH} and before matcher routing. Commonly used for filtered listeners and
     * middleware-style logic.
     */
    NORMAL,

    /**
     * Highest priority, reserved for default event behavior.
     * <p>
     * Only one HIGH listener may exist per event type. This listener runs first and typically performs the core
     * action represented by the event.
     * <p>
     * Examples:
     * <ul>
     *   <li>{@link DropItemEvent}: performs the actual item drop.</li>
     *   <li>{@link PickupItemEvent}: performs the actual item pickup.</li>
     * </ul>
     */
    HIGH
}
