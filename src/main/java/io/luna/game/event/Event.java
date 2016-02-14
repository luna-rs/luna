package io.luna.game.event;

/**
 * An event passed through an {@link EventPipeline} to be intercepted by {@link EventFunction}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class Event {

    /**
     * The {@link EventPipeline} this event is currently passing through, {@code null} if this event is not currently passing
     * through a pipeline.
     */
    private EventPipeline pipeline;

    /**
     * @return {@code true} if the underlying pipeline was successfully terminated, and {@code false} if {@code pipeline}
     * field is {@code null} or the pipeline is already terminated.
     */
    public boolean terminate() {
        return pipeline != null && pipeline.terminate();
    }

    /**
     * @return The {@link EventPipeline} this event is currently passing through, {@code null} if this event is not currently
     * passing through a pipeline.
     */
    public EventPipeline getPipeline() {
        return pipeline;
    }

    /**
     * Sets the {@link EventPipeline} this event is currently passing through.
     */
    public void setPipeline(EventPipeline pipeline) {
        this.pipeline = pipeline;
    }
}
