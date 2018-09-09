package io.luna.game.task;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

/**
 * A model containing functions to handle processing of tasks.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class TaskManager {

    /**
     * A list of tasks awaiting execution.
     */
    private final List<Task> awaitingList = new LinkedList<>();

    /**
     * A queue of tasks ready to be executed.
     */
    private final Queue<Task> readyQueue = new ArrayDeque<>();

    /**
     * Schedules a new task to be ran.
     *
     * @param t The task to schedule.
     */
    public void schedule(Task t) {
        t.onSchedule();
        if (t.isInstant()) {
            t.runTask();
        }
        awaitingList.add(t);
    }

    /**
     * A function that runs an iteration of task processing.
     */
    public void runTaskIteration() {

        // Run through all tasks awaiting execution.
        Iterator<Task> iterator = awaitingList.iterator();
        while (iterator.hasNext()) {
            Task it = iterator.next();

            // Remove task if it was cancelled.
            if (!it.isRunning()) {
                iterator.remove();
                continue;
            }

            it.onLoop();

            /* If it's ready to execute, add to execution queue. We pass task to different collection
            to avoid ConcurrentModificationException when tasks are scheduled within tasks.  */
            if (it.canExecute()) {
                readyQueue.add(it);
            }
        }

        // Poll execution queue and run all tasks.
        for (; ; ) {
            Task it = readyQueue.poll();
            if (it == null) {
                break;
            }
            it.runTask();
        }
    }

    /**
     * Cancels active tasks with the argued attachment.
     *
     * @param attachment The attachment to cancel tasks with.
     */
    public void cancel(Object attachment) {
        awaitingList.stream().filter(it -> Objects.equals(attachment, it.getAttachment().orElse(null)))
                .forEach(Task::cancel);
    }
}
