package io.luna.game.event;

import io.luna.game.plugin.RuntimeScript;
import io.luna.game.plugin.ScriptExecutionException;
import io.luna.util.ReflectionUtils;

import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkState;

/**
 * A model that can be matched to an event to by an {@link EventMatcher}.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class EventMatcherListener<E extends Event> {

    /**
     * The script containing this matcher listener.
     */
    private final RuntimeScript script;

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
    public void setScript(RuntimeScript newScript) {
        checkState(script == null, "RuntimeScript already set.");
        ReflectionUtils.setField(this, "script", newScript);
    }

    /**
     * @return The script containing this matcher listener. Possibly {@code null}.
     */
    public RuntimeScript getScript() {
        return script;
    }

    /**
     * @return The listener.
     */
    public Consumer<E> getListener() {
        return listener;
    }
}