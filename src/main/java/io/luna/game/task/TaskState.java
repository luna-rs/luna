package io.luna.game.task;

/**
 * An enumerated type whose elements represent the possible states that a {@link Task} can be in.
 *
 * @author lare96 
 */
public enum TaskState {

    /**
     * The {@code IDLE} state. The task has not yet been scheduled or cancelled.
     */
    IDLE,

    /**
     * The {@code RUNNING} state. The task was scheduled.
     */
    RUNNING,

    /**
     * The {@code CANCELLED} state. The task was cancelled.
     */
    CANCELLED
}
