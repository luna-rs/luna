package io.luna.game;

import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.Uninterruptibles;
import io.luna.LunaContext;
import io.luna.game.model.World;
import io.luna.game.task.Task;
import io.luna.net.msg.out.SystemUpdateMessageWriter;
import io.luna.util.ExecutorUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

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
     * The world instance.
     */
    private final World world;

    /**
     * A thread pool for low-priority tasks.
     */
    private final ListeningExecutorService threadPool = ExecutorUtils.newCachedThreadPool();

    /**
     * Creates a new {@link GameService}.
     *
     * @param context The context instance.
     */
    public GameService(LunaContext context) {
        this.context = context;
        world = context.getWorld();
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
            gracefulShutdown();
        } catch (Exception e) {
            LOGGER.fatal("Luna could not be terminated gracefully.");
        }
        
        System.exit(0);
    }

    /**
     * Runs all pending synchronization tasks in the backing queue. This allows other Threads to execute
     * game logic on the main game thread.
     */
    private void runSynchronizationTasks() {
        Runnable task;
        
        while ((task = syncTasks.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                LOGGER.catching(e);
            }
        }
    }

    /**
     * Performs a graceful shutdown of Luna. A shutdown performed in this way allows Luna to properly
     * save resources before the application exits. This method will wait for as long as it needs to
     * for all important threads to complete their remaining tasks.
     */
    private void gracefulShutdown() {
        World world = context.getWorld();
        LOGGER.info("Luna is being gracefully terminated. The application will exit once completed.");

        // Run any pending synchronization tasks.
        runSynchronizationTasks();

        // Disconnect and save all players.
        world.getPlayers().clear();

        // Wait for any last minute tasks to complete.
        threadPool.shutdown();
        
        while (!threadPool.isTerminated()) {
            Uninterruptibles.sleepUninterruptibly(2, TimeUnit.SECONDS);
        }
    }

    /**
     * Schedules a system update in {@code ticks} amount of time.
     *
     * @param ticks The amount of ticks to schedule for.
     */
    public void scheduleSystemUpdate(int ticks) {
        var packet = new SystemUpdateMessageWriter(ticks);
        
        // Send out system update messages.
        world.getPlayers().forEach(player -> player.queue(packet));

        // Schedule a graceful shutdown once the system update timer completes.
        world.schedule(new Task(ticks + 5) {
            @Override
            protected void execute() {
                stopAsync();
            }
        });
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
     * Runs a result-bearing and listening asynchronous task. <strong>Warning: Tasks may not be ran right away, as
     * there is a limit to how large the backing pool can grow to. This is to prevent DOS type attacks.</strong> If you
     * require a faster pool for higher priority tasks, consider using a dedicated pool from {@link ExecutorUtils}.
     *
     * @param t The task to run.
     * @return The result of {@code t}.
     */
    public <T> ListenableFuture<T> submit(Callable<T> t) {
        return threadPool.submit(t);
    }

    /**
     * Runs a listening asynchronous task. <strong>Warning: Tasks may not be ran right away, as there is a limit to
     * how large the backing pool can grow to. This is to prevent DOS type attacks.</strong> If you require a faster
     * pool for higher priority tasks, consider using a dedicated pool from {@link ExecutorUtils}.
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
