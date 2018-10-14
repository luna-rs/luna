package io.luna.game.event;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Iterables;
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
     * The encompassing script name.
     */
    private final String scriptName;

    /**
     * The type of event being intercepted.
     */
    private final Class<?> eventType;

    /**
     * The arguments.
     */
    private final EventArguments args;

    /**
     * The listener function.
     */
    private final Consumer<E> listener;

    /**
     * Creates a new {@link EventListener}.
     *
     * @param eventType The type of event being intercepted.
     * @param args The arguments.
     * @param listener The listener function.
     */
    public EventListener(Class<?> eventType, EventArguments args, Consumer<E> listener) {
        this.eventType = eventType;
        this.listener = listener;
        this.args = args;

        // Value injected with reflection.
        scriptName = null;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("eventType", eventType.getSimpleName()).
                add("args", Iterables.toString(args)).toString();
    }

    /**
     * Applies the wrapped function and handles exceptions.
     *
     * @param msg The event to apply the function with.
     */
    public void apply(E msg) {
        try {
            if (args == EventArguments.NO_ARGS) {
                listener.accept(msg);
            } else if (msg.matches(args)) {
                listener.accept(msg);
                msg.terminate();
            }
        } catch (Exception failure) {
            throw new ScriptExecutionException(this, failure);
        }
    }

    /**
     * Sets the encompassing script name to {@code name}.
     *
     * @param name The name to set to.
     */
    public void setScriptName(String name) {
        checkState(scriptName == null, "Script name already set.");
        ReflectionUtils.setField(this, "scriptName", name);
    }

    /**
     * @return The encompassing script name.
     */
    public String getScriptName() {
        checkState(scriptName != null, "Script name cannot be <null>.");
        return scriptName;
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

    /**
     * @return The arguments.
     */
    public EventArguments getArgs() {
        return args;
    }
}
