package io.luna.game.event;

import com.google.common.base.MoreObjects;

/**
 * A wrapper for the declaration of event types.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EventType {

    /**
     * The event type.
     */
    private final Class<? extends Event> type;

    /**
     * Creates a new {@link EventType}.
     *
     * @param type The event type.
     */
    public EventType(Class<? extends Event> type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("name", type.getName()).toString();
    }

    /**
     * @return The event type.
     */
    public Class<? extends Event> getType() {
        return type;
    }
}
