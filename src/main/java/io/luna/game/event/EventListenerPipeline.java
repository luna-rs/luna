package io.luna.game.event;

import io.github.classgraph.ClassInfo;
import io.luna.game.model.Entity;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.interact.InteractionActionListener;
import io.luna.game.plugin.Script;
import io.luna.game.plugin.ScriptExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Dispatch pipeline for a single {@link Event} subtype.
 * <p>
 * Each pipeline owns the listeners registered for one concrete {@code eventType} and is responsible for invoking
 * them in a stable, deterministic order based on {@link EventPriority}. Dispatch order is:
 * <ol>
 *   <li>the single {@link EventPriority#HIGH} listener, if present</li>
 *   <li>all {@link EventPriority#NORMAL} listeners, in registration order</li>
 *   <li>the configured {@link EventMatcher}, if any keyed or filtered routing applies</li>
 *   <li>all {@link EventPriority#LOW} listeners, in registration order</li>
 * </ol>
 * <p>
 * Pipelines are also used to build deferred interaction actions through {@link #getInteractionListeners(Player, Entity, Event)},
 * preserving the same execution order used by normal posting.
 *
 * @param <E> The event type handled by this pipeline.
 * @author lare96
 */
public final class EventListenerPipeline<E extends Event> {

    /**
     * Logger used for reporting listener execution failures.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The concrete event type routed through this pipeline.
     */
    private final Class<E> eventType;

    /**
     * The single high-priority listener for this event type.
     * <p>
     * This listener always executes first and is typically reserved for the event's primary or default behavior.
     * Only one {@link EventPriority#HIGH} listener may exist per pipeline.
     */
    private EventListener<E> priorityListener;

    /**
     * Registered normal-priority listeners.
     * <p>
     * These listeners execute after {@link #priorityListener} and before {@link #matcher}, in insertion order.
     */
    private final List<EventListener<E>> listeners = new ArrayList<>();

    /**
     * Registered low-priority listeners.
     * <p>
     * These listeners execute last, after normal listeners and matcher dispatch, in insertion order.
     */
    private final List<EventListener<E>> lazyListeners = new ArrayList<>();

    /**
     * Matcher-based dispatch layer for keyed or filtered event routing.
     * <p>
     * This acts as an optimization and specialization layer that can dispatch directly to a narrower set of listeners
     * without requiring all listeners to be iterated manually.
     * <p>
     * Defaults to {@link EventMatcher#defaultMatcher()}.
     */
    private EventMatcher<E> matcher;

    /**
     * Creates a new {@link EventListenerPipeline} for a specific event type.
     *
     * @param eventType The concrete event type routed through this pipeline.
     */
    public EventListenerPipeline(Class<E> eventType) {
        this.eventType = eventType;
        matcher = EventMatcher.defaultMatcher();
    }

    /**
     * Dispatches an event through this pipeline immediately.
     * <p>
     * The event is temporarily associated with this pipeline for the duration of dispatch via
     * {@link Event#setPipeline(EventListenerPipeline)} so downstream listeners can inspect the originating pipeline
     * if needed.
     * <p>
     * Any {@link ScriptExecutionException} thrown during listener execution is caught and reported through
     * {@link #handleException(ScriptExecutionException)}. The pipeline association is always cleared afterward.
     *
     * @param msg The event instance to dispatch.
     */
    public void post(E msg) {
        try {
            msg.setPipeline(this);
            internalPost(msg);
        } catch (ScriptExecutionException e) {
            handleException(e);
        } finally {
            msg.setPipeline(null);
        }
    }

    /**
     * Builds interaction-aware listener actions for {@code msg} in normal dispatch order.
     * <p>
     * Instead of executing listeners immediately, this method wraps each listener into an
     * {@link InteractionActionListener} so interaction processing can defer execution and apply each listener's
     * policy against the provided {@link Player}.
     * <p>
     * The returned list preserves the same order used by {@link #post(Event)}.
     *
     * @param player The player used to resolve each listener's interaction policy.
     * @param target The target the player is interacting with.
     * @param msg The event that will be supplied to each deferred listener action.
     * @return A new ordered list of interaction listener actions for the event.
     */
    public List<InteractionActionListener> getInteractionListeners(Player player, Entity target, E msg) {
        List<InteractionActionListener> pending = new ArrayList<>();

        // HIGH listener always runs first.
        if (priorityListener != null) {
            pending.add(new InteractionActionListener(priorityListener.getPolicy().apply(player, target),
                    () -> priorityListener.apply(msg)));
        }

        // Then NORMAL listeners.
        for (EventListener<E> listener : listeners) {
            pending.add(new InteractionActionListener(listener.getPolicy().apply(player, target),
                    () -> listener.apply(msg)));
        }

        // Then matcher routing (key-based / filtered).
        pending.addAll(matcher.interactions(player, msg));

        // Then LOW listeners.
        for (EventListener<E> listener : lazyListeners) {
            pending.add(new InteractionActionListener(listener.getPolicy().apply(player, target),
                    () -> listener.apply(msg)));
        }
        return pending;
    }

    /**
     * Performs the actual dispatch work without applying pipeline wiring or top-level exception handling.
     * <p>
     * Listener invocation order is:
     * <ol>
     *   <li>{@link #priorityListener}</li>
     *   <li>{@link #listeners}</li>
     *   <li>{@link #matcher}</li>
     *   <li>{@link #lazyListeners}</li>
     * </ol>
     *
     * @param msg The event to dispatch.
     */
    private void internalPost(E msg) {
        // HIGH listener always runs first.
        if (priorityListener != null) {
            priorityListener.getListener().accept(msg);
        }

        // Then NORMAL listeners.
        for (EventListener<E> listener : listeners) {
            listener.apply(msg);
        }

        // Then matcher routing (key-based / filtered).
        matcher.match(msg);

        // Then LOW listeners.
        for (EventListener<E> listener : lazyListeners) {
            listener.apply(msg);
        }
    }

    /**
     * Handles a listener-side {@link ScriptExecutionException}.
     * <p>
     * When the exception is associated with a {@link Script}, the script's class metadata is logged to make the
     * failing listener easier to identify. Otherwise, the exception is logged directly.
     *
     * @param e The exception thrown during listener execution.
     */
    private void handleException(ScriptExecutionException e) {
        Script script = e.getScript();
        if (script != null) {
            ClassInfo info = script.getInfo();
            logger.warn("Failed to run a listener from script '{}' in package '{}'",
                    info.getSimpleName(), info.getPackageName(), e);
        } else {
            logger.catching(e);
        }
    }

    /**
     * Adds a listener to this pipeline according to its {@link EventPriority}.
     * <p>
     * Registration order is preserved within the normal and low-priority listener lists.
     * <p>
     * Only one {@link EventPriority#HIGH} listener may exist for a pipeline. Attempting to register a second one
     * will throw an {@link IllegalStateException}.
     *
     * @param listener The listener to register.
     * @throws IllegalStateException If a high-priority listener is already registered and another one is added.
     */
    public void add(EventListener<E> listener) {
        switch (listener.getPriority()) {
            case LOW:
                lazyListeners.add(listener);
                break;
            case NORMAL:
                listeners.add(listener);
                break;
            case HIGH:
                if (priorityListener != null) {
                    throw new IllegalStateException("Only one high priority event listener {" +
                            listener.getEventType().getSimpleName() + "} can exist per event type!");
                }
                priorityListener = listener;
                break;
        }
    }

    /**
     * Replaces the matcher used by this pipeline.
     * <p>
     * The new matcher will be used for all future dispatches and interaction-listener generation.
     *
     * @param newMatcher The matcher implementation to install.
     */
    public void setMatcher(EventMatcher<E> newMatcher) {
        matcher = newMatcher;
    }
}