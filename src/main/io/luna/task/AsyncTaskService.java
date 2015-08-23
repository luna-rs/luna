package io.luna.task;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class AsyncTaskService extends AbstractExecutionThreadService {

    // TODO: Documentation.

    /**
     * The amount of threads dedicated to this asynchronous task service.
     */
    private final int threadCount;

    /**
     * A queue containing the executable tasks.
     */
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    private final ExecutorService service = getService();

    /**
     * Creates a new {@link io.luna.task.AsyncTaskService}. Is private to
     * ensure usage of the static factory methods.
     *
     * @param threadCount
     *            the amount of threads to allocate, {@code 0} for scaling
     *            threads.
     */
    private AsyncTaskService(int threadCount) {
        Preconditions.checkArgument(threadCount >= 0, "threadCount < 0");
        this.threadCount = threadCount;
    }

    public static AsyncTaskService newService(int threadCount) {
        return new AsyncTaskService(threadCount);
    }

    public static AsyncTaskService newService() {
        return new AsyncTaskService(Runtime.getRuntime().availableProcessors());
    }

    public static AsyncTaskService newScalingThreadService() {
        return new AsyncTaskService(0);
    }

    @Override
    protected void startUp() throws Exception {
        if (tasks.size() == 0) {
            throw new IllegalStateException("Task count must be > 0");
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <p>
     * Illegal invocation of this method will cause unpredictable results, and
     * should be avoided at all costs.
     */
    @Override
    protected void run() throws Exception {
        CountDownLatch latch = new CountDownLatch(tasks.size());

        for (;;) {
            Runnable t = tasks.poll();
            if (t == null) {
                break;
            }

            // Wrap the asynchronous task within a runnable, now we can pass it
            // through our executor and include the CountDownLatch code.
            service.execute(() -> {
                try {
                    t.run();
                } catch (Exception e) {
                    e.printStackTrace(); // Just in case the task doesn't catch
                                         // exceptions.
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // Wait until tasks are completed.
        service.shutdown(); // Destroy threads to free up resources.
        stopAsync(); // Cancel service once tasks are completed.
    }

    @Override
    protected void triggerShutdown() {
        try {
            tasks.clear();
            service.shutdownNow();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected String serviceName() {
        return AsyncTaskService.class.getSimpleName() + "Thread";
    }

    public void add(Runnable t) {
        if (state() == State.NEW) {
            tasks.add(t);
        } else {
            throw new IllegalStateException("state != NEW");
        }
    }

    private ExecutorService getService() {
        ThreadFactory tf = new ThreadFactoryBuilder().setNameFormat("AsyncTaskServiceWorkerThread").build();
        return threadCount == 0 ? Executors.newCachedThreadPool(tf) : Executors.newFixedThreadPool(threadCount, tf);
    }
}
