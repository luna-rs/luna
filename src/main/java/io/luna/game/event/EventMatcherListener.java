package io.luna.game.event;

import io.luna.game.plugin.Script;
import io.luna.game.plugin.ScriptExecutionException;
import io.luna.util.ReflectionUtils;

import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkState;

/**
 * Script-aware listener wrapper used by {@link EventMatcher} implementations.
 * <p>
 * This mirrors {@link EventListener} but is intended for matcher-backed dispatch paths where listeners are stored
 * in key-based structures.
 *
 * @param <E> The event type handled by this matcher listener.
 */
public final class EventMatcherListener<E extends Event> {

    /**
     * The script that declared/owns this matcher listener.
     * <p>
     * Injected after construction and may be {@code null} until registration completes.
     */
    private final Script script;

    /**
     * The callback executed when a matching event is dispatched.
     */
    private final Consumer<E> listener;

    /**
     * Creates a new {@link EventMatcherListener}.
     *
     * @param listener The callback to run when a matching event is dispatched.
     */
    public EventMatcherListener(Consumer<E> listener) {
        this.listener = listener;
        script = null;
    }

    /**
     * Executes the callback for {@code msg}.
     *
     * <p>Any exception thrown by the callback is wrapped into a {@link ScriptExecutionException}
     * so the dispatcher can attribute/log it against the owning script.
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
     * Injects the script that owns this matcher listener.
     *
     * <p>Intended to be called once during registration/loading.
     *
     * @param newScript The script to associate with this matcher listener.
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
     * @return The callback executed when a matching event is dispatched.
     */
    public Consumer<E> getListener() {
        return listener;
    }
}
