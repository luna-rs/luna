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
 * <p>
 * The TaskManager is responsible for scheduling, executing, and managing the lifecycle of {@link Task} instances.
 * It maintains two collections:
 * <ul>
 *   <li>A list of pending tasks that are waiting to be executed based on their delay</li>
 *   <li>A queue of tasks that are ready to be executed in the current game tick</li>
 * </ul>
 * <p>
 * The task system is designed to be run on the game thread and provides a way to schedule delayed
 * or periodic actions without blocking the main game loop. Each game tick (typically 600ms),
 * the {@link #runTaskIteration()} method is called to process all pending tasks.
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
        if (task.getState() == TaskState.IDLE) {
            if (!task.onSchedule()) {
                task.cancel();
                return;
            }
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

            /* We pass tasks to a different collection to avoid a ConcurrentModificationException if tasks
            are scheduled within tasks. */
            executing.add(task);
        }

        // Poll execution queue and run all tasks.
        for (; ; ) {
            Task task = executing.poll();
            if (task == null) {
                break;
            }
            try {
                task.onProcess();
                if (task.isReady()) {
                    task.runTask();
                }
            } catch (Exception e) {
                task.onException(e);
            }
        }
    }

    /**
     * Applies {@code action} to every task that has {@code key} as an attachment key.
     *
     * @param key The attachment.
     * @param action The action.
     */
    public void forEach(Object key, Consumer<Task> action) {
        for (Task task : pending) {
            Object foundKey = task.getKey().orElse(null);
            if (Objects.equals(foundKey, key)) {
                action.accept(task);
            }
        }
    }
}
