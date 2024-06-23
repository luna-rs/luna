package io.luna.game.task;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;

/**
 * A model containing functions to handle processing of tasks.
 *
 * @author lare96
 */
public final class TaskManager {

    /**
     * A list of tasks awaiting execution.
     */
    private final List<Task> pending = new LinkedList<>();

    /**
     * A queue of tasks ready to be executed.
     */
    private final Queue<Task> executing = new ArrayDeque<>();

    /**
     * Schedules a new task to be ran.
     *
     * @param task The task to schedule.
     */
    public void schedule(Task task) {
        if (task.getState() == TaskState.IDLE && task.onSchedule()) {
            task.setState(TaskState.RUNNING);

            if (task.isInstant()) {
                task.runTask();
            }
            pending.add(task);
        }
    }

    /**
     * A function that runs an iteration of task processing.
     */
    public void runTaskIteration() {

        // Run through all tasks awaiting execution.
        Iterator<Task> iterator = pending.iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();

            // Remove task if it was cancelled.
            if (task.getState() == TaskState.CANCELLED) {
                iterator.remove();
                continue;
            }


            /* If it's ready to execute, add to execution queue. We pass tasks to a different collection
            to avoid a ConcurrentModificationException if tasks are scheduled within tasks.  */
            task.onProcess();
            if (task.isReady()) {
                executing.add(task);
            }
        }

        // Poll execution queue and run all tasks.
        for (; ; ) {
            Task task = executing.poll();
            if (task == null) {
                break;
            }
            task.runTask();
        }
    }

    /**
     * Applies {@code action} to every task that has {@code attachment} as an attachment.
     *
     * @param attachment The attachment.
     * @param action The action.
     */
    public void forEachAttachment(Object attachment, Consumer<Task> action) {
        for (Task task : pending) {
            Object taskAttachment = task.getAttachment().orElse(null);
            if (Objects.equals(taskAttachment, attachment)) {
                action.accept(task);
            }
        }
    }
}
