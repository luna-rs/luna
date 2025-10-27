package io.luna.game.event;

/**
 * Represents a message object passed through an event pipeline. Events are expected to be
 * immutable to avoid unintended side effects during processing. The pipeline reference is an
 * exception to allow internal routing.
 *
 * <p><strong>Note:</strong> The pipeline reference is only non-null during dispatch.</p>
 *
 * @author lare96
 */
public class Event {

    /**
     * The current pipeline this event is being routed through.
     */
    private EventListenerPipeline<?> pipeline;

    /**
     * @return The pipeline currently dispatching this event, or {@code null} if none.
     */
    public EventListenerPipeline<?> getPipeline() {
        return pipeline;
    }

    /**
     * Sets the dispatching pipeline. Intended for internal use only.
     *
     * @param pipeline The pipeline to associate with this event.
     */
    protected void setPipeline(EventListenerPipeline<?> pipeline) {
        this.pipeline = pipeline;
    }
}
