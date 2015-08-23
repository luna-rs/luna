package io.luna.task;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import io.luna.Luna;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * An {@link com.google.common.util.concurrent.AbstractExecutionThreadService}
 * that asynchronously executes a collection of tasks, which can then themselves
 * be executed sequentially or concurrently. This {@code Service} automatically
 * terminates itself when all tasks have finished executing.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class AsyncTaskService extends AbstractExecutionThreadService {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(Luna.class);

    /**
     * The amount of threads dedicated to this asynchronous task service.
     */
    private final int threadCount;

    /**
     * A queue containing the executable tasks.
     */
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    /**
     * The executor that will execute our tasks.
     */
    private final ExecutorService service = getService();

    /**
     * Creates a new {@link io.luna.task.AsyncTaskService}. Is private to
     * encourage usage of the static factory methods.
     *
     * @param threadCount
     *            The amount of threads to allocate, {@code 0} for scaling
     *            threads.
     */
    private AsyncTaskService(int threadCount) {
        checkArgument(threadCount >= 0, "threadCount < 0");
        this.threadCount = threadCount;
    }

    /**
     * Constructs a new asynchronous task service backed by {@code threadCount}
     * threads.
     * 
     * @param threadCount
     *            The amount of threads to allocate.
     * @return The asynchronous task service.
     */
    public static AsyncTaskService newService(int threadCount) {
        return new AsyncTaskService(threadCount);
    }

    /**
     * Constructs a new asynchronous task service backed by
     * {@code Runtime.getRuntime().availableProcessors()} threads.
     * 
     * @return The asynchronous task service.
     */
    public static AsyncTaskService newService() {
        return new AsyncTaskService(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Constructs a new asynchronous task service that will scale intelligently
     * with the workload. It is backed by a cached thread pool.
     * 
     * @return The asynchronous task service.
     */
    public static AsyncTaskService newScalingThreadService() {
        return new AsyncTaskService(0);
    }

    @Override
    protected void startUp() throws Exception {
        checkState(tasks.size() > 0);
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
        Phaser phaser = new Phaser(tasks.size());

        for (;;) {
            Runnable t = tasks.poll();

            if (t == null) {
                break;
            }

            service.execute(() -> {
                try {
                    t.run();
                } catch (Exception e) {
                    LOGGER.catching(e);
                } finally {
                    phaser.arrive();
                }
            });
        }

        phaser.awaitAdvance(0); // Wait until tasks are completed.
        service.shutdown(); // Executor is no longer needed, terminate it.
        stopAsync(); // Cancel service once tasks are completed.
    }

    @Override
    protected void triggerShutdown() {
        try {
            tasks.clear(); // Stop tasks from being submitted.
            service.shutdownNow(); // Cancel tasks awaiting execution.
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected String serviceName() {
        return AsyncTaskService.class.getSimpleName() + "Thread";
    }

    /**
     * Adds {@code t} to the internal queue of tasks. This {@code Service}
     * <b>must</b> be in a {@code NEW} state for tasks to be submitted.
     * 
     * @param t
     *            The task to enqueue.
     */
    public void add(Runnable t) {
        checkState(state() == State.NEW, "state != NEW");
        tasks.add(t);
    }

    /**
     * Constructs an {@link java.util.concurrent.ExecutorService} based on the
     * {@code threadCount}.
     * 
     * @return The executor that will execute the queued tasks.
     */
    private ExecutorService getService() {
        ThreadFactory tf = new ThreadFactoryBuilder().setNameFormat("AsyncTaskServiceWorkerThread").build();
        return threadCount == 0 ? Executors.newCachedThreadPool(tf) : Executors.newFixedThreadPool(threadCount, tf);
    }
}
