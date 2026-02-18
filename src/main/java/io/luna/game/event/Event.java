package io.luna.game.event;

/**
 * Base type for all events dispatched through Luna's event system.
 * <p>
 * Events are intended to be treated as immutable message objects to prevent side effects while multiple listeners
 * process the same instance. The only mutable field is the pipeline reference, which is set by the dispatcher to
 * support internal routing/inspection during dispatch.
 * <p>
 * <strong>Dispatch note:</strong> {@link #getPipeline()} is non-{@code null} only while the event is actively being
 * dispatched, and is cleared afterwards.
 *
 * @author lare96
 */
public class Event {

    /**
     * The pipeline currently dispatching this event.
     * <p>
     * This value is set immediately before dispatch and cleared immediately after dispatch. It should be treated as
     * internal state and not relied on for long-lived logic.
     */
    private EventListenerPipeline<?> pipeline;

    /**
     * Returns the pipeline currently dispatching this event.
     *
     * @return The active dispatch pipeline, or {@code null} if this event is not currently in flight.
     */
    public EventListenerPipeline<?> getPipeline() {
        return pipeline;
    }

    /**
     * Sets the dispatching pipeline reference.
     * <p>
     * Intended for internal use by the event dispatcher only.
     *
     * @param pipeline The pipeline to associate with this event for the duration of dispatch.
     */
    protected void setPipeline(EventListenerPipeline<?> pipeline) {
        this.pipeline = pipeline;
    }
}
