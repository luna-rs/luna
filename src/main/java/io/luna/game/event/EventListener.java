package io.luna.game.event;

import com.google.common.base.MoreObjects;
import io.luna.game.plugin.Script;
import io.luna.game.plugin.ScriptExecutionException;
import io.luna.util.ReflectionUtils;

import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkState;

/**
 * A script-bound listener for events of type {@code E}.
 * <p>
 * Listeners are typically declared in scripts and registered into an {@link EventListenerPipeline}. When an event is
 * dispatched, the pipeline invokes {@link #apply(Event)} in an order determined by {@link EventPriority}.
 * <p>
 * The owning {@link Script} is injected after construction (via reflection) so that exceptions can be attributed to
 * the correct script during error reporting.
 *
 * @param <E> The event type handled by this listener.
 * @author lare96
 */
public final class EventListener<E extends Event> {

    /**
     * The script that declared/owns this listener.
     * <p>
     * Injected after construction and may be {@code null} until registration completes.
     */
    private final Script script;

    /**
     * The event type this listener accepts.
     */
    private final Class<E> eventType;

    /**
     * The callback to run when an event is dispatched to this listener.
     */
    private final Consumer<E> listener;

    /**
     * The dispatch priority for this listener.
     */
    private final EventPriority priority;

    /**
     * Creates a new {@link EventListener}.
     *
     * @param eventType The event type this listener accepts.
     * @param listener The callback to run when the event is dispatched.
     * @param priority The dispatch priority.
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
        return MoreObjects.toStringHelper(this)
                .add("eventType", eventType.getSimpleName())
                .toString();
    }

    /**
     * Executes the callback for {@code msg}.
     * <p>
     * Any exception thrown by the listener is wrapped into a {@link ScriptExecutionException} so the dispatcher can
     * report it against the owning script.
     *
     * @param msg The event instance.
     */
    public void apply(E msg) {
        try {
            listener.accept(msg);
        } catch (Exception failure) {
            throw new ScriptExecutionException(script, failure);
        }
    }

    /**
     * Injects the script that owns this listener.
     * <p>
     * Intended to be called once during registration/loading.
     *
     * @param newScript The script to associate with this listener.
     */
    public void setScript(Script newScript) {
        checkState(script == null, "Script already set.");
        ReflectionUtils.setField(this, "script", newScript);
    }

    /**
     * @return The owning script (may be {@code null} before injection).
     */
    public Script getScript() {
        return script;
    }

    /**
     * @return The event type this listener accepts.
     */
    public Class<E> getEventType() {
        return eventType;
    }

    /**
     * @return The callback executed when an event is dispatched.
     */
    public Consumer<E> getListener() {
        return listener;
    }

    /**
     * @return The listener dispatch priority.
     */
    public EventPriority getPriority() {
        return priority;
    }
}
