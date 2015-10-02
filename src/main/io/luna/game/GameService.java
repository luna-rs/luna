package io.luna.game;

import io.netty.util.internal.StringUtil;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * An {@link AbstractScheduledService} implementation that performs general game
 * logic processing, provides functionality for executing small asynchronous and
 * concurrent tasks through a cached thread pool, and allows for tasks from
 * other threads to be executed on the game logic thread.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class GameService extends AbstractScheduledService {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(GameService.class);

    /**
     * A cached thread pool that manages the execution of short, low priority,
     * asynchronous and concurrent tasks.
     */
    private final ExecutorService executorService = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("GameServiceWorkerThread").build());

    /**
     * A queue of synchronization tasks.
     */
    private final Queue<Runnable> syncTasks = new ConcurrentLinkedQueue<>();

    /**
     * A counter that determines how many ticks have passed since this
     * {@code GameService} was started.
     */
    private final AtomicLong tickCount = new AtomicLong();

    @Override
    protected String serviceName() {
        return StringUtil.simpleClassName(this) + "Thread";
    }

    /**
     * {@inheritDoc}
     * <p>
     * <p>
     * This method should <b>never</b> be invoked unless by the underlying
     * {@link AbstractScheduledService}. Illegal invocation of this method will
     * lead to serious gameplay timing issues as well as other unexplainable and
     * unpredictable issues related to gameplay.
     */
    @Override
    protected void runOneIteration() throws Exception {
        for (;;) {
            Runnable t = syncTasks.poll();
            if (t == null) {
                break;
            }

            try {
                t.run();
            } catch (Exception e) {
                LOGGER.catching(e);
            }
        }
        tickCount.incrementAndGet();
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedRateSchedule(600, 600, TimeUnit.MILLISECONDS);
    }

    /**
     * Prints a message that this service has been terminated, and attempts to
     * gracefully exit the application cleaning up resources and ensuring all
     * players are logged out. If an exception is thrown during shutdown, the
     * shutdown process is aborted completely and the application is exited.
     */
    @Override
    protected void shutDown() {
        try {
            LOGGER.fatal("The asynchronous game service has been shutdown, exiting...");
            // TODO: Logout players, clean up any additional resources, etc.
            syncTasks.forEach(Runnable::run);
            syncTasks.clear();
            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            tickCount.set(0);
        } catch (Exception e) {
            LOGGER.catching(Level.FATAL, e);
        } finally {
            System.exit(0);
        }
    }

    /**
     * Queues {@code t} to be executed on this game service thread.
     * 
     * @param t The task to be queued.
     */
    public void queueTask(Runnable t) {
        syncTasks.add(t);
    }

    /**
     * Executes {@code t} using the backing cached thread pool. Tasks submitted
     * this way should generally be short and low priority.
     * 
     * @param t The task to execute.
     */
    public void execute(Runnable t) {
        executorService.execute(t);
    }

    /**
     * Gets the amount of ticks that have elapsed since this application
     * started. This function is thread-safe.
     * 
     * @return The amount of ticks that have elapsed.
     */
    public long getTicks() {
        return tickCount.get();
    }
}
