package io.luna.game.event;

import java.util.Optional;

/**
 * An Object passed through a pipeline to be intercepted by event listeners. Events should
 * <strong>always</strong> be immutable to ensure that they cannot be modified while being passed
 * through the pipeline.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class Event {

    /**
     * The pipeline this event is passing through.
     */
    private Optional<EventListenerPipeline> pipeline = Optional.empty();

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
}
