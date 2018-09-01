package io.luna.game;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import io.luna.LunaContext;
import io.luna.game.model.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.luna.util.ThreadUtils.nameThreadFactory;
import static io.luna.util.ThreadUtils.newCachedThreadPool;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * An {@link AbstractScheduledService} implementation that handles the launch, processing, and termination
 * of the main game service.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class GameService extends AbstractScheduledService {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * A queue of synchronization tasks.
     */
    private final Queue<Runnable> syncTasks = new ConcurrentLinkedQueue<>();

    /**
     * The context instance.
     */
    private final LunaContext context;

    /**
     * A thread pool for low-priority tasks.
     */
    private final ListeningExecutorService threadPool = newCachedThreadPool(nameThreadFactory("LunaWorkerThread"));

    /**
     * Creates a new {@link GameService}.
     *
     * @param context The context instance.
     */
    public GameService(LunaContext context) {
        this.context = context;
    }

    @Override
    protected String serviceName() {
        return "LunaGameThread";
    }

    @Override
    protected void runOneIteration() {
        try {
            // Do stuff from other threads.
            runSynchronizationTasks();

            // Run the main game loop.
            World world = context.getWorld();
            world.loop();
        } catch (Exception e) {
            LOGGER.catching(e);
        }
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedRateSchedule(600, 600, TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutDown() {
        try {
            switch (state()) {
                case FAILED:
                    // The service stopped unexpectedly.
                    errorShutdown();
                    return;
                case TERMINATED:
                    // We stopped the service ourselves.
                    gracefulShutdown();
                    return;
            }
        } catch (Exception e) {
            LOGGER.catching(e);
        }
        System.exit(0);
    }

    /**
     * Runs all pending synchronization tasks in the backing queue. This allows other Threads to execute
     * game logic on the main game thread.
     */
    private void runSynchronizationTasks() {
        for (; ; ) {
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
    }

    /**
     * Performs a graceful shutdown of Luna. A shutdown performed in this way allows Luna to
     * properly save resources before the application exits. This method will wait for as long as it needs
     * to for all important threads to complete their remaining tasks.
     */
    private void gracefulShutdown() {
        Stopwatch shutdownTimer = Stopwatch.createStarted();
        World world = context.getWorld();

        // Run any pending synchronization tasks.
        runSynchronizationTasks();

        // Disconnect and save all players.
        world.getPlayers().clear();

        // Wait for any last minute low-priority tasks to complete.
        threadPool.shutdown();
        while (!threadPool.isTerminated()) {
            sleepUninterruptibly(2, TimeUnit.SECONDS);
        }

        LOGGER.info("Luna was gracefully shutdown in {}s. The application will now exit...",
                box(shutdownTimer.elapsed(TimeUnit.SECONDS)));
    }

    /**
     * Performs a shutdown of Luna when an error is the cause. A last ditch attempt to save player data
     * is made and the application terminates.
     */
    private void errorShutdown() {
        try {
            World world = context.getWorld();

            // Do a sequential save of players.
            world.getPlayers().forEach(plr -> plr.save(false));
        } finally {
            LOGGER.fatal("Luna terminated unexpectedly, exiting...", failureCause());
        }
    }

    /**
     * Queues a task to be ran on the next tick.
     *
     * @param t The task to run.
     */
    public void sync(Runnable t) {
        syncTasks.add(t);
    }

    /**
     * Runs a result-bearing and listening asynchronous task.
     *
     * @param t The task to run.
     * @return The result of {@code t}.
     */
    public <T> ListenableFuture<T> submit(Callable<T> t) {
        return threadPool.submit(t);
    }

    /**
     * Runs a listening asynchronous task.
     *
     * @param t The task to run.
     * @return The result of {@code t}.
     */
    public ListenableFuture<?> submit(Runnable t) {
        return threadPool.submit(t);
    }

    /**
     * @return The context instance.
     */
    public LunaContext getContext() {
        return context;
    }
}
