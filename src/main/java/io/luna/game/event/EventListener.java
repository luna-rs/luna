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
 * @author lare96 <http://github.org/lare96>
 */
public final class EventListener<E extends Event> {

    /**
     * The encompassing script.
     */
    private final Script script;

    /**
     * The type of event being intercepted.
     */
    private final Class<?> eventType;

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
    public EventListener(Class<?> eventType, Consumer<E> listener) {
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
            throw new ScriptExecutionException(this, failure);
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
     * @return The encompassing script.
     */
    public Script getScript() {
        checkState(script != null, "Script cannot be <null>.");
        return script;
    }

    /**
     * @return The type of event being intercepted.
     */
    public Class<?> getEventType() {
        return eventType;
    }

    /**
     * @return The listener function.
     */
    public Consumer<E> getListener() {
        return listener;
    }
}
