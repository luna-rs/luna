package io.luna.game.event;

import io.luna.game.plugin.Script;
import io.luna.game.plugin.ScriptExecutionException;
import io.luna.util.ReflectionUtils;

import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkState;

/**
 * A script-aware event handler used in conjunction with {@link EventMatcher}.
 *
 * @param <E> The type of event handled.
 */
public final class EventMatcherListener<E extends Event> {

    /**
     * The script containing this matcher listener.
     */
    private final Script script;

    /**
     * The listener.
     */
    private final Consumer<E> listener;

    /**
     * Creates a new {@link EventMatcherListener}.
     *
     * @param listener The listener.
     */
    public EventMatcherListener(Consumer<E> listener) {
        this.listener = listener;
        script = null;
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
     * Sets the script containing this matcher listener.
     *
     * @param newScript The script to set to.
     */
    public void setScript(Script newScript) {
        checkState(script == null, "Script already set.");
        ReflectionUtils.setField(this, "script", newScript);
    }

    /**
     * @return The script containing this matcher listener. Possibly {@code null}.
     */
    public Script getScript() {
        return script;
    }

    /**
     * @return The listener.
     */
    public Consumer<E> getListener() {
        return listener;
    }
}