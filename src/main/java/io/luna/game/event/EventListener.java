package io.luna.game.event;

import com.google.common.base.MoreObjects;
import io.luna.game.plugin.Script;
import io.luna.game.plugin.ScriptExecutionException;
import io.luna.util.ReflectionUtils;

import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkState;

/**
 * A listener that intercepts events.
 *
 * @param <E> The type of event being intercepted.
 * @author lare96
 */
public final class EventListener<E extends Event> {

    /**
     * The encompassing script.
     */
    private final Script script;

    /**
     * The type of event being intercepted.
     */
    private final Class<E> eventType;

    /**
     * The listener function.
     */
    private final Consumer<E> listener;

    /**
     * Creates a new {@link EventListener}.
     *
     * @param eventType The type of event being intercepted.
     * @param listener The listener function.
     */
    public EventListener(Class<E> eventType, Consumer<E> listener) {
        this.eventType = eventType;
        this.listener = listener;

        // Value injected with reflection.
        script = null;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("eventType", eventType.getSimpleName()).toString();
    }

    /**
     * Applies the wrapped function and handles exceptions.
     *
     * @param msg The event to apply the function with.
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
     * @return The type of event being intercepted.
     */
    public Class<E> getEventType() {
        return eventType;
    }

    /**
     * @return The listener function.
     */
    public Consumer<E> getListener() {
        return listener;
    }
}
