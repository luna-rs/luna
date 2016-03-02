package io.luna.game.event;

/**
 * An event passed through an {@link EventListenerPipeline} to be intercepted by {@link EventListener}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class Event {

    /**
     * The {@link EventListenerPipeline} this event is currently passing through, {@code null} if this event is not currently
     * passing through a pipeline.
     */
    private EventListenerPipeline pipeline;

    /**
     * Determines if this event should be intercepted, when given {@code args}. Always returns {@code true} if not overridden
     * regardless of the arguments.
     *
     * @param args The arguments for this event.
     * @return {@code true} if this event can be intercepted, {@code false} otherwise.
     */
    public boolean matches(Object... args) {
        return true;
    }

    /**
     * @return {@code true} if the underlying pipeline was successfully terminated, and {@code false} if {@code pipeline}
     * field is {@code null} or the pipeline is already terminated.
     */
    public boolean terminate() {
        return pipeline != null && pipeline.terminate();
    }

    /**
     * @return The {@link EventListenerPipeline} this event is currently passing through, {@code null} if this event is not
     * currently passing through a pipeline.
     */
    public EventListenerPipeline getPipeline() {
        return pipeline;
    }

    /**
     * Sets the {@link EventListenerPipeline} this event is currently passing through.
     */
    protected void setPipeline(EventListenerPipeline pipeline) {
        this.pipeline = pipeline;
    }
}
