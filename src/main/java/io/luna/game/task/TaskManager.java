package io.luna.game.task;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * A list of tasks awaiting execution.
     */
    private final List<Task> awaitingExecution = new LinkedList<>();

    /**
     * A queue of tasks ready to be executed.
     */
    private final Queue<Task> executionQueue = new ArrayDeque<>();

    /**
     * Schedules a new task to be ran.
     */
    public void schedule(Task t) {
        t.onSchedule();
        if (t.isInstant()) {
            try {
                t.execute();
            } catch (Exception e) {
                t.onException(e);
                LOGGER.catching(e);
            }
        }
        awaitingExecution.add(t);
    }

    /**
     * A function that runs an iteration of task processing.
     */
    public void runTaskIteration() {
        Iterator<Task> iterator = awaitingExecution.iterator();
        while (iterator.hasNext()) {
            Task it = iterator.next();

            if (!it.isRunning()) {
                iterator.remove();
                continue;
            }
            it.onLoop();
            if (it.canExecute()) {
                executionQueue.add(it);
            }
        }

        for (; ; ) {
            Task it = executionQueue.poll();
            if (it == null) {
                break;
            }

            try {
                it.execute();
            } catch (Exception e) {
                it.onException(e);
                LOGGER.catching(e);
            }
        }
    }

    /**
     * Cancels active tasks with the argued attachment.
     */
    public void cancel(Object attachment) {
        awaitingExecution.stream().filter(it -> Objects.equals(attachment, it.getAttachment().orElse(null)))
            .forEach(Task::cancel);
    }
}
