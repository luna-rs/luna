package io.luna.game.event;

import com.google.common.base.MoreObjects;
import io.luna.game.plugin.Script;
import io.luna.game.plugin.ScriptExecutionException;
import io.luna.util.ReflectionUtils;

import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkState;

/**
 * A listener that reacts to events of type {@code E}. Typically bound to plugin scripts.
 *
 * @param <E> The event type this listener handles.
 * @author lare96
 */
public final class EventListener<E extends Event> {

    /**
     * The encompassing script.
     */
    private final Script script;

    /**
     * The event class this listener is associated with.
     */
    private final Class<E> eventType;

    /**
     * The logic to run when the event is dispatched.
     */
    private final Consumer<E> listener;

    /**
     * The priority of this event listener.
     */
    private final EventPriority priority;

    /**
     * Creates a new {@link EventListener}.
     *
     * @param eventType The event class this listener is associated with.
     * @param listener The logic to run when the event is dispatched.
     * @param priority The priority of this event listener.
     */
    public EventListener(Class<E> eventType, Consumer<E> listener, EventPriority priority) {
        this.eventType = eventType;
        this.listener = listener;
        this.priority = priority;

        // Value injected with reflection.
        script = null;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("eventType", eventType.getSimpleName()).toString();
    }

    /**
     * Executes the listener logic.
     *
     * @param msg The event to handle.
     */
    public void apply(E msg) {
        try {
            listener.accept(msg);
        } catch (Exception failure) {
            throw new ScriptExecutionException(script, failure);
        }
    }

    /**
     * Sets the encompassing script to {@code newScript}.
     *
     * @param newScript The script to set to.
     */
    public void setScript(Script newScript) {
        checkState(script == null, "Script already set.");
        ReflectionUtils.setField(this, "script", newScript);
    }

    /**
     * @return The encompassing script. Possibly {@code null}.
     */
    public Script getScript() {
        return script;
    }

    /**
     * @return The event class this listener is associated with.
     */
    public Class<E> getEventType() {
        return eventType;
    }

    /**
     * @return The logic to run when the event is dispatched.
     */
    public Consumer<E> getListener() {
        return listener;
    }

    /**
     * @return The priority of this event listener.
     */
    public EventPriority getPriority() {
        return priority;
    }
}
