package io.luna.game.task;

/**
 * An enumerated type whose elements represent the possible states that a {@link Task} can be in.
 * <p>
 * Tasks transition between these states during their lifecycle:
 * <ul>
 *   <li>Tasks start in the {@code IDLE} state when created</li>
 *   <li>When scheduled with {@link TaskManager#schedule(Task)}, they move to {@code RUNNING}</li>
 *   <li>When cancelled with {@link Task#cancel()}, they move to {@code CANCELLED}</li>
 * </ul>
 * <p>
 * The {@link TaskManager} uses these states to determine which tasks to process and which to remove.
 *
 * @author lare96
 */
public enum TaskState {

    /**
     * The {@code IDLE} state. The task has not yet been scheduled or cancelled.
     * This is the initial state of all newly created tasks.
     */
    IDLE,

    /**
     * The {@code RUNNING} state. The task was scheduled and is being processed
     * by the {@link TaskManager} on each game tick.
     */
    RUNNING,

    /**
     * The {@code CANCELLED} state. The task was cancelled and will be removed
     * from the {@link TaskManager} on the next processing cycle.
     */
    CANCELLED
}
