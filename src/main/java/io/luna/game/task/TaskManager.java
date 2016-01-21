package io.luna.game.task;

import io.luna.game.GameService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static java.util.Objects.requireNonNull;

/**
 * Handles the processing and execution of {@link Task}s. Functions contained within this class should only be invoked on the
 * {@link GameService} thread to ensure thread safety.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class TaskManager {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(TaskManager.class);

    /**
     * A {@link List} of tasks that have been submitted and are awaiting execution.
     */
    private final List<Task> awaitingExecution = new LinkedList<>();

    /**
     * A {@link Queue} of tasks that are ready to be executed.
     */
    private final Queue<Task> executionQueue = new ArrayDeque<>();

    /**
     * Schedules {@code t} to run in the underlying {@code TaskManager}.
     *
     * @param t The {@link Task} to schedule.
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
     * Runs an iteration of the {@link Task} processing logic. All {@link Exception}s thrown by {@code Task}s are caught and
     * logged by the underlying {@link Logger}.
     */
    public void runTaskIteration() {
        Iterator<Task> $it = awaitingExecution.iterator();
        while ($it.hasNext()) {
            Task it = $it.next();

            if (!it.isRunning()) {
                $it.remove();
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
     * Iterates through all active {@link Task}s and cancels all that have {@code attachment} as their attachment.
     */
    public void cancel(Object attachment) {
        requireNonNull(attachment);
        awaitingExecution.stream().filter(it -> attachment.equals(it.getAttachment().orElse(null))).forEach(Task::cancel);
    }
}
