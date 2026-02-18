package io.luna.game;

import api.bot.GameCoroutineScope;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import io.luna.LunaContext;
import io.luna.game.event.Event;
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent;
import io.luna.game.event.impl.ServerStateChangedEvent.ServerShutdownEvent;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.plugin.PluginBootstrap;
import io.luna.game.task.Task;
import io.luna.net.msg.out.SystemUpdateMessageWriter;
import io.luna.util.ExecutorUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.util.concurrent.Futures.getUnchecked;
import static com.google.common.util.concurrent.Uninterruptibles.awaitTerminationUninterruptibly;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * Main game loop service.
 *
 * <p>{@link GameService} owns the single “game thread” and executes the world tick at a fixed rate
 * (typically 600ms per tick). It also provides safe cross-thread scheduling utilities so other
 * threads (Netty, persistence workers, bot systems, etc.) can request work to run on the game thread.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Bootstrap: load Kotlin plugins/scripts and start the {@link World}.</li>
 *   <li>Tick loop: run {@link World#process()} at a fixed cadence.</li>
 *   <li>Synchronization: drain {@link #syncTasks} to run queued logic on the game thread.</li>
 *   <li>Lifecycle: coordinate graceful shutdown and system updates.</li>
 *   <li>Utilities: provide {@link #sync(Supplier)} and {@link #submit(Supplier)} helpers.</li>
 * </ul>
 *
 * <p><strong>Threading model:</strong> All game state mutation should occur on the game thread.
 * Use {@link #sync(Runnable)} / {@link #sync(Supplier)} (or {@link #getGameExecutor()}) to marshal
 * logic onto the game thread from other threads.
 *
 * @author lare96
 */
public final class GameService extends AbstractScheduledService {

    /**
     * Executor that guarantees execution on the game thread.
     *
     * <p>If invoked from the game thread, the command runs immediately.
     * Otherwise the command is enqueued into {@link #syncTasks} and executed at the start of the next tick.
     */
    private final class GameServiceExecutor implements Executor {

        @Override
        public void execute(Runnable command) {
            if (Thread.currentThread() == thread) {
                command.run();
            } else {
                syncTasks.add(command);
            }
        }
    }

    /**
     * Service listener that responds to lifecycle transitions of the underlying scheduled service.
     *
     * <p>Most “startup sequence” logic is performed when the service enters {@link State#RUNNING},
     * and shutdown coordination is handled when the service is stopping or terminated.
     */
    private final class GameServiceListener extends Service.Listener {

        @Override
        public void running() {
            // Start the game world and run startup logic from Kotlin scripts.
            loadPlugins();
            runSynchronizationTasks();
            world.start();

            // Players won't be able to log in until startup tasks are complete, so it's fine to block the game thread.
            runKotlinTasks(ServerLaunchEvent::new, "Waiting for Kotlin startup tasks to complete...");

            // Release the lock in LunaServer so networking can begin accepting logins.
            onlineLock.complete(null);
        }

        @Override
        public void stopping(State from) {
            // A request to terminate gracefully has been made.
            logger.fatal("Gracefully terminating Luna...");
        }

        @Override
        public void terminated(State from) {
            // The game thread was gracefully terminated.
            logger.fatal("The application will now exit.");
            System.exit(0);
        }

        @Override
        public void failed(State from, Throwable failure) {
            // An exception was thrown on the game thread.
            logger.fatal("Luna has been terminated because of an uncaught exception!", failure);
            System.exit(1);
        }

        /**
         * Initializes {@link PluginBootstrap}, which loads Kotlin plugins/scripts and prepares event listeners.
         *
         * <p>This is executed on the game thread as part of startup.
         */
        private void loadPlugins() {
            try {
                PluginBootstrap bootstrap = new PluginBootstrap(context);
                bootstrap.start();

                int pluginCount = context.getPlugins().getPluginCount();
                int scriptCount = context.getPlugins().getScriptCount();
                logger.info("{} Kotlin plugins containing {} scripts have been loaded.",
                        box(pluginCount), box(scriptCount));
            } catch (Exception e) {
                logger.fatal("Error loading plugins!", e);
                System.exit(1);
            }
        }
    }

    /** Async logger. */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Queue of “synchronization tasks” to be executed on the game thread.
     *
     * <p>These tasks are drained at the beginning of each tick by {@link #runSynchronizationTasks()}.
     */
    private final Queue<Runnable> syncTasks = new ConcurrentLinkedQueue<>();

    /** Executor that marshals work onto the game thread via {@link #syncTasks}. */
    private final GameServiceExecutor gameExecutor;

    /**
     * Online gate used by {@link io.luna.LunaServer} to prevent accepting logins until startup is complete.
     *
     * <p>Completed when the game thread has started, plugins are loaded, and startup tasks have finished.
     */
    private final CompletableFuture<Void> onlineLock = new CompletableFuture<>();

    /** Context handle (cache/world/plugins/etc.). */
    private final LunaContext context;

    /** World state owned by this server instance. */
    private final World world;

    /**
     * General-purpose worker pool for low-overhead async tasks.
     *
     * <p>This pool is intentionally bounded/managed by {@link ExecutorUtils} to reduce attack surface for
     * untrusted workloads.
     */
    private final ExecutorService pool;

    /**
     * The game thread instance. Set during {@link #startUp()}.
     */
    private volatile Thread thread;

    /**
     * Creates a new {@link GameService} for the supplied {@link LunaContext}.
     *
     * @param context The server context.
     */
    public GameService(LunaContext context) {
        this.context = context;
        world = context.getWorld();
        pool = ExecutorUtils.threadPool(serviceName() + "Worker");
        gameExecutor = new GameServiceExecutor();
        addListener(new GameServiceListener(), MoreExecutors.directExecutor());
    }

    @Override
    protected void runOneIteration() {
        process();
    }

    @Override
    protected Scheduler scheduler() {
        // 600ms game tick cadence.
        return Scheduler.newFixedRateSchedule(600, 600, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void startUp() {
        thread = Thread.currentThread();
        thread.setName("GameThread");
    }

    @Override
    public void shutDown() {
        try {
            gracefulShutdown();
        } catch (Exception e) {
            logger.fatal("Luna could not be terminated gracefully!", e);
        }
    }

    /**
     * Runs one full tick: synchronization stage + world processing stage.
     *
     * <p>Any exceptions thrown while processing are caught and logged so a single failure does not
     * permanently stall the tick loop.
     */
    private void process() {
        try {
            // Drain tasks requested by other threads.
            runSynchronizationTasks();

            // Run the main game loop.
            world.process();
        } catch (Exception e) {
            logger.catching(e);
        }
    }

    /**
     * Executes all pending synchronization tasks enqueued from other threads.
     *
     * <p>This is the mechanism that allows external threads to safely run logic on the main game thread:
     * tasks are added via {@link #sync(Supplier)} or {@link #getGameExecutor()} and executed here.
     */
    private void runSynchronizationTasks() {
        for (;;) {
            var runnable = syncTasks.poll();
            if (runnable == null) {
                break;
            }
            try {
                runnable.run();
            } catch (Exception e) {
                logger.catching(e);
            }
        }
    }

    /**
     * Dispatches a server-state event to Kotlin scripts and blocks until any asynchronous work completes.
     *
     * <p>This helper creates a temporary background pool, constructs an event (which carries the pool),
     * posts it, and then waits for all script-launched async tasks to finish.
     *
     * @param eventFunction Produces the event instance (given the temporary pool).
     * @param waitingMessage Log message printed while waiting.
     */
    private <E extends Event> void runKotlinTasks(Function<ExecutorService, E> eventFunction, String waitingMessage) {
        ExecutorService pool = ExecutorUtils.threadPool("BackgroundLoaderThread");
        E msg = eventFunction.apply(pool);
        context.getPlugins().post(msg);
        pool.shutdown();
        logger.info(waitingMessage);
        awaitTerminationUninterruptibly(pool);
    }

    /**
     * Performs a graceful shutdown sequence.
     *
     * <p>This method runs on the game thread, so player/world state can be manipulated without additional
     * synchronization. The sequence aims to:
     * <ul>
     *   <li>Stop coroutines and script activity</li>
     *   <li>Stop accepting new logins</li>
     *   <li>Persist all players</li>
     *   <li>Disconnect online players</li>
     *   <li>Close the cache and shutdown persistence services</li>
     *   <li>Drain general-purpose task pools</li>
     * </ul>
     *
     * <p>This method blocks until all critical shutdown steps complete.
     */
    private void gracefulShutdown() {
        var world = context.getWorld();
        var loginService = world.getLoginService();
        var logoutService = world.getLogoutService();

        // Stop all coroutines.
        GameCoroutineScope.INSTANCE.shutdown();

        // Run last minute game tasks from other threads.
        runSynchronizationTasks();

        // Run shutdown code from Kotlin scripts, and wait for the asynchronous portions to complete.
        runKotlinTasks(ServerShutdownEvent::new, "Waiting for Kotlin shutdown tasks to complete...");

        // Will stop any current and future logins.
        loginService.stopAsync().awaitTerminated();

        // Save all players and wait for it to complete.
        getUnchecked(world.getPersistenceService().saveAll());

        // Synchronously disconnect all players.
        world.getPlayers().forEach(Player::forceLogout);

        // Close cache resource.
        context.getCache().close();

        // Close logout resources.
        logoutService.stopAsync().awaitTerminated();

        // Wait for general-purpose tasks to complete.
        pool.shutdown();
        awaitTerminationUninterruptibly(pool);
    }

    /**
     * Schedules a system update countdown and triggers a graceful shutdown after it completes.
     *
     * <p>This broadcasts a client system update timer to all online players and schedules a shutdown
     * slightly after the countdown finishes.
     *
     * @param ticks Countdown length in game ticks.
     */
    public void scheduleSystemUpdate(int ticks) {
        // Preliminary save of all players (fire-and-forget).
        world.getPersistenceService().saveAll();

        // Send out system update messages.
        for (Player player : world.getPlayers()) {
            player.queue(new SystemUpdateMessageWriter(ticks));
        }

        // Schedule a graceful shutdown once the system update timer completes.
        world.schedule(new Task(ticks + 5) {
            @Override
            protected void execute() {
                stopAsync();
            }
        });
    }

    /**
     * Convenience overload for {@link #sync(Supplier)} when no return value is needed.
     *
     * @param t The task to run on the game thread.
     * @return A future completed when the task has run.
     */
    public CompletableFuture<Void> sync(Runnable t) {
        return sync(() -> {
            t.run();
            return null;
        });
    }

    /**
     * Queues work to run on the game thread and returns a {@link CompletableFuture} for its result.
     *
     * <p>If called from the game thread, the work is executed immediately (no queue hop).
     * Otherwise, it is enqueued and executed at the start of the next tick.
     *
     * <p>This is the preferred API for safely mutating game state from worker threads.
     *
     * @param s The supplier to execute on the game thread.
     * @return A future completed with the supplier's result.
     */
    public <T> CompletableFuture<T> sync(Supplier<T> s) {
        CompletableFuture<T> result = new CompletableFuture<>();
        gameExecutor.execute(() -> {
            try {
                result.complete(s.get());
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    /**
     * Submits a result-bearing asynchronous task to the general-purpose pool.
     *
     * <p><strong>Warning:</strong> tasks may not execute immediately due to pool growth limits intended
     * to mitigate DoS-style workloads. For latency-sensitive tasks, use a dedicated pool from
     * {@link ExecutorUtils}.
     *
     * @param t The task to run.
     * @return A future completed with the task's result.
     */
    public <T> CompletableFuture<T> submit(Supplier<T> t) {
        return CompletableFuture.supplyAsync(t, pool);
    }

    /**
     * Submits an asynchronous task to the general-purpose pool.
     *
     * <p><strong>Warning:</strong> tasks may not execute immediately due to pool growth limits intended
     * to mitigate DoS-style workloads. For latency-sensitive tasks, use a dedicated pool.
     *
     * @param t The task to run.
     * @return A future completed when the task finishes.
     */
    public CompletableFuture<Void> submit(Runnable t) {
        return CompletableFuture.runAsync(t, pool);
    }

    /** @return The context instance. */
    public LunaContext getContext() {
        return context;
    }

    /**
     * Returns an {@link Executor} that guarantees execution on the game thread.
     *
     * <p>This can be used when you want “fire-and-forget” marshalling without the overhead of a
     * {@link CompletableFuture} (see {@link #sync(Supplier)}).
     */
    public Executor getGameExecutor() {
        return gameExecutor;
    }

    /**
     * Returns the online lock that gates login acceptance until startup completes.
     *
     * @return The online lock future.
     */
    public CompletableFuture<Void> getOnlineLock() {
        return onlineLock;
    }

    /**
     * Returns the current game thread instance.
     *
     * @return The game thread.
     */
    public Thread getThread() {
        return thread;
    }
}
