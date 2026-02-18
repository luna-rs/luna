package io.luna.game.action;

/**
 * Represents the life-cycle state of an {@link Action} as it moves through an {@link ActionQueue}.
 * <p>
 * States generally progress as:
 * <pre>
 * NEW -> PROCESSING -> (COMPLETED | INTERRUPTED)
 * </pre>
 * <p>
 * {@link #COMPLETED} indicates the action ended by its own logic. {@link #INTERRUPTED} indicates the action was
 * forcibly stopped by the queue (or a higher-priority interruption call) while it was processing.
 *
 * @author lare96
 */
public enum ActionState {

    /**
     * Initial state when an {@link Action} instance is constructed and has not yet been queued.
     */
    NEW,

    /**
     * The action has been submitted to an {@link ActionQueue} and is actively being processed.
     * <p>
     * While in this state, the action’s scheduling hooks (submit/process/run) may be invoked according to the
     * queue’s rules.
     */
    PROCESSING,

    /**
     * The action finished normally and will be removed from the queue.
     * <p>
     * This is typically entered when the action signals completion (e.g. returns {@code true} from its run loop or
     * calls a completion helper).
     */
    COMPLETED,

    /**
     * The action was forcibly stopped while {@link #PROCESSING}.
     * <p>
     * This is typically entered when the queue interrupts actions (e.g. weak/strong removal rules or {@code interrupt*}
     * APIs) before the action finishes naturally.
     */
    INTERRUPTED
}
