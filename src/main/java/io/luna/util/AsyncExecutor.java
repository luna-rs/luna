package io.luna.util;

import com.google.common.util.concurrent.Uninterruptibles;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * An {@code Executor} that runs a sequence of tasks and waits until they're completed to continue.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class AsyncExecutor implements Executor {

    private static final ThreadFactory DEFAULT_THREAD_FACTORY =
            ThreadUtils.nameThreadFactory("AsyncExecutorWorkerThread");

    /**
     * The tasks.
     */
    private final Queue<Future<?>> pendingTasks = new ConcurrentLinkedQueue<>();

    /**
     * The delegate executor.
     */
    private final ExecutorService delegate;

    private final boolean isSingleton;


    public static AsyncExecutor newSingletonExecutor(int nThreads) {
        return new AsyncExecutor(nThreads, DEFAULT_THREAD_FACTORY, true);
    }

    public static AsyncExecutor newSingletonExecutor(int nThreads, ThreadFactory threadFactory) {
        return new AsyncExecutor(nThreads, threadFactory, true);
    }

    public static AsyncExecutor newExecutor(int nThreads) {
        return new AsyncExecutor(nThreads, DEFAULT_THREAD_FACTORY, false);
    }

    public static AsyncExecutor newExecutor(int nThreads, ThreadFactory threadFactory) {
        return new AsyncExecutor(nThreads, threadFactory, false);
    }

    private AsyncExecutor(int nThreads, ThreadFactory threadFactory, boolean isSingleton) {
        this.isSingleton = isSingleton;
        delegate = Executors.newFixedThreadPool(nThreads, threadFactory);
    }


    @Override
    public void execute(Runnable command) {
        pendingTasks.add(delegate.submit(command));
    }

    /**
     * Runs tasks and waits for them to complete.
     */
    public void await(long timeout, TimeUnit unit) throws ExecutionException {
        if (isDone()) {
            pendingTasks.clear();
        } else {
            for (; ; ) {
                Future<?> pending = pendingTasks.poll();
                if (pending == null) {
                    break;
                }
                // TODO handle cancellation exception?
                Uninterruptibles.getUninterruptibly(pending);
            }
        }

        if (isSingleton) {
            delegate.shutdown();

            try {
                delegate.awaitTermination(timeout, unit);
            } catch (InterruptedException e) {
                // Ignore thread interruption.
            }
        }
    }

    public int size() {
        return pendingTasks.size();
    }

    public void await() throws ExecutionException {
        await(Long.MAX_VALUE, TimeUnit.DAYS);
    }

    public boolean isDone() {
        return pendingTasks.stream().allMatch(Future::isDone);
    }
}