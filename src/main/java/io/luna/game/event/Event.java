package io.luna.game.event;

/**
 * An Object passed through a pipeline to be intercepted by event listeners. Events should <strong>always</strong> be
 * immutable to ensure that they cannot be modified while being passed through the pipeline.
 *
 * @author lare96
 */
public class Event {

    /**
     * The pipeline this event is passing through. Might be {@code null}, always {@code null} for events that are
     * {@code lazy-posted} (see {@link EventListenerPipeline#lazyPost(Event)}).
     */
    private EventListenerPipeline<?> pipeline;

    /**
     * Returns the pipeline instance.
     */
    public EventListenerPipeline<?> getPipeline() {
        return pipeline;
    }

    /**
     * Sets a new pipeline instance.
     */
    protected void setPipeline(EventListenerPipeline<?> pipeline) {
        // The only time an event should be mutable is when changing the pipeline.
        this.pipeline = pipeline;
    }
}
