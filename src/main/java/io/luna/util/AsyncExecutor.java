package io.luna.util;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.Uninterruptibles;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import static com.google.common.base.Preconditions.checkState;

/**
 * An {@link Executor} implementation that asynchronously executes a series of user-defined tasks, and
 * provides functionality to wait until they're completed.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class AsyncExecutor implements Executor {

    /**
     * A queue of pending tasks.
     */
    private final Queue<ListenableFuture<?>> pendingTasks = new ConcurrentLinkedQueue<>();

    /**
     * The thread pool worker count.
     */
    private final int threadCount;

    /**
     * The thread factory to create workers with.
     */
    private final ThreadFactory threadFactory;

    /**
     * The backing thread pool containing workers that execute tasks.
     */
    private final ListeningExecutorService threadPool;

    /**
     * Creates a new {@link AsyncExecutor}.
     *
     * @param threadCount The thread pool worker count.
     * @param threadFactory The thread factory to create workers with.
     */
    public AsyncExecutor(int threadCount, ThreadFactory threadFactory) {
        this.threadCount = threadCount;
        this.threadFactory = threadFactory;

        // Create thread pool.
        ExecutorService delegate = Executors.newFixedThreadPool(threadCount, threadFactory);
        threadPool = MoreExecutors.listeningDecorator(delegate);
    }

    /**
     * Creates a new {@link AsyncExecutor} using a named thread factory.
     *
     * @param threadCount The thread pool worker count.
     * @param threadName The name of worker threads.
     */
    public AsyncExecutor(int threadCount, String threadName) {
        this(threadCount, new ThreadFactoryBuilder().setNameFormat(threadName).build());
    }

    /**
     * Creates a new {@link AsyncExecutor} using the default thread factory.
     *
     * @param threadCount The thread pool worker count.
     */
    public AsyncExecutor(int threadCount) {
        this(threadCount, new ThreadFactoryBuilder().setNameFormat("AsyncExecutorThread").build());
    }

    @Override
    public void execute(Runnable command) {
        checkState(isRunning(), "No workers available to run tasks."); // TODO change message

        ListenableFuture<?> pending = threadPool.submit(command);
        pendingTasks.offer(pending);
    }

    /**
     * Waits as long as necessary for all pending tasks to complete, performing shutdown operations if
     * necessary. When this method returns successfully, {@link #size()} {@code == 0}.
     *
     * @param terminate If the backing thread pool should be terminated once all tasks finish.
     * @throws ExecutionException If a pending task throws an exception.
     */
    public void await(boolean terminate) throws ExecutionException {
        checkState(isRunning(), "Backing thread pool has already been terminated.");

        for (;;) {
            Future<?> pending = pendingTasks.poll();

            if (pending == null) {
                break;
            }

            Uninterruptibles.getUninterruptibly(pending);
        }

        if (terminate) {
            threadPool.shutdown();
            ThreadUtils.awaitTerminationUninterruptibly(threadPool);
        }
    }

    /**
     * Returns the current amount of pending tasks. The returned amount is guaranteed to not include
     * tasks that have already completed.
     *
     * @return The amount of pending tasks.
     */
    public int size() { //TODO rename to getPendingCount or something
        pendingTasks.removeIf(Future::isDone);
        return pendingTasks.size();
    }


    /**
     * Returns if all tasks have completed their execution.
     *
     * @return {@code true} if all pending tasks are done.
     */
    public boolean isDone() {
        return size() == 0;
    }

    /**
     * Returns if this executor is running. If {@code false} new tasks cannot be added and
     * {@link #size()} {@code == 0}.
     *
     * @return {@code true} if the backing thread pool isn't shutdown.
     */
    public boolean isRunning() {
        return !threadPool.isShutdown();
    }

    /**
     * @return The thread pool worker count.
     */
    public int getThreadCount() {
        return threadCount;
    }

    /**
     * @return The thread factory to create workers with.
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }
}