package io.luna.game.event;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * An Object passed through a pipeline to be intercepted by event listeners. Events should
 * <strong>always</strong> be immutable to ensure that they cannot be modified while being passed
 * through the pipeline.
 * <p>
 * When writing getters for events, "get" should be omitted in order to conform to Scala conventions.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class Event {

    /**
     * The pipeline this event is passing through.
     */
    private Optional<EventListenerPipeline> pipeline = Optional.empty();

    /**
     * The identifier for this event. Used to retrieve event listeners from the listener map.
     */
    private final OptionalInt mapId;

    /**
     * Creates a new {@link Event}.
     *
     * @param mapId The identifier for this event.
     */
    public Event(int mapId) {
        this.mapId = OptionalInt.of(mapId);
    }

    /**
     * Creates a new {@link Event} with no identifier.
     */
    public Event() {
        mapId = OptionalInt.empty();
    }

    /**
     * Matches the event listener's arguments against this event. Returns {@code true} by default. Is ignored
     * if {@link #mapId} is present.
     *
     * @param args The event listener's arguments.
     * @return {@code true} if this event matches the event listener's arguments.
     */
    public boolean matches(EventArguments args) {
        return true;
    }

    /**
     * Terminates the passing of this event through a pipeline.
     *
     * @return {@code false} if the event was not terminated.
     */
    public boolean terminate() {
        if (pipeline.isPresent()) {
            EventListenerPipeline current = pipeline.get();
            return current.terminate();
        }
        return false;
    }

    /**
     * @return The pipeline this event is passing through.
     */
    public Optional<EventListenerPipeline> pipeline() {
        return pipeline;
    }

    /**
     * Sets a new pipeline instance. This is used to terminate traversal of the event.
     *
     * @param pipeline The next pipeline to traverse.
     */
    public void pipeline(EventListenerPipeline pipeline) {
        this.pipeline = Optional.ofNullable(pipeline);
    }

    /**
     * @return The identifier for this event.
     */
    OptionalInt getMapId() {
        return mapId;
    }
}
