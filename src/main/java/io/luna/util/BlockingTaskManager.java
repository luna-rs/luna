package io.luna.util;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * An {@code Executor} that runs a sequence of tasks and waits until they're completed to continue.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class BlockingTaskManager {

    /**
     * A task within a blocking task manager.
     */
    private interface BlockingTask extends Consumer<CountDownLatch> {
    }

    /**
     * The tasks.
     */
    private final List<BlockingTask> tasks = new LinkedList<>();

    /**
     * The delegate executor.
     */
    private final Executor delegate;

    /**
     * Creates a new {@link BlockingTaskManager}.
     *
     * @param delegate The delegate executor.
     */
    public BlockingTaskManager(Executor delegate) {
        this.delegate = delegate;
    }

    /**
     * This method <strong>does not</strong> actually run the task. Use {@code await()} to run all
     * tasks at once.
     */
    public void submit(Runnable r) {
        tasks.add(barrier -> {
            try {
                r.run();
            } finally {
                barrier.countDown();
            }
        });
    }

    /**
     * Runs tasks and waits for them to complete.
     */
    public void await() throws InterruptedException {
        CountDownLatch barrier = new CountDownLatch(tasks.size());
        for (BlockingTask bt : tasks) {
            delegate.execute(() -> bt.accept(barrier));
        }
        barrier.await();
    }
}