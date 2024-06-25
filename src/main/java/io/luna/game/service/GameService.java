package io.luna.game.service;

import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import io.luna.LunaContext;
import io.luna.LunaServer;
import io.luna.game.event.Event;
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent;
import io.luna.game.event.impl.ServerStateChangedEvent.ServerShutdownEvent;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.task.Task;
import io.luna.net.msg.out.SystemUpdateMessageWriter;
import io.luna.util.AsyncExecutor;
import io.luna.util.ExecutorUtils;
import io.luna.util.ThreadUtils;
import io.luna.util.benchmark.BenchmarkType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.google.common.util.concurrent.Futures.getUnchecked;
import static io.luna.util.ThreadUtils.awaitTerminationUninterruptibly;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * An {@link AbstractScheduledService} implementation that handles the launch, processing, and termination
 * of the main game thread.
 *
 * @author lare96
 */
public final class GameService extends AbstractScheduledService {

    /**
     * An {@link Executor} implementation that will run all code on the game thread, using {@link #sync(Runnable)}.
     */
    private final class GameServiceExecutor implements Executor {

        @Override
        public void execute(Runnable command) {
            sync(command);
        }
    }

    /**
     * A listener that will be notified of any changes in the game thread's state.
     */
    private final class GameServiceListener extends Service.Listener {

        @Override
        public void running() {
            // Start the game world and run startup logic from Kotlin scripts.
            sync(() -> {
                world.start();
                runKotlinTasks(ServerLaunchEvent::new, "Waiting for {} Kotlin startup task(s) to complete...");

                // Synchronizes with the lock in LunaServer. Players won't be able to login until the startup
                // tasks are complete, so it's fine to block the game thread.
                synchronizer.countDown();
            });
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
            System.exit(0);
        }
    }

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * A queue of synchronization tasks.
     */
    private final Queue<Runnable> syncTasks = new ConcurrentLinkedQueue<>();

    /**
     * The synchronization executor.
     */
    private final GameServiceExecutor gameExecutor = new GameServiceExecutor();

    /**
     * The synchronizer for the Kotlin startup tasks.
     */
    private final CountDownLatch synchronizer = new CountDownLatch(1);

    /**
     * The context instance.
     */
    private final LunaContext context;

    /**
     * The world instance.
     */
    private final World world;

    /**
     * The server instance.
     */
    private final LunaServer server;

    /**
     * A thread pool for general purpose low-overhead tasks.
     */
    private final ListeningExecutorService fastPool;

    /**
     * Creates a new {@link GameService}.
     *
     * @param context The context instance.
     */
    public GameService(LunaContext context) {
        this.context = context;
        world = context.getWorld();
        server = context.getServer();
        fastPool = ExecutorUtils.threadPool(serviceName() + "Worker");
        addListener(new GameServiceListener(), MoreExecutors.directExecutor());
    }

    @Override
    protected void runOneIteration() {
        server.getBenchmarkManager().startBenchmark(BenchmarkType.GAME_LOOP);
        loop();
        server.getBenchmarkManager().finishBenchmark(BenchmarkType.GAME_LOOP);
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
            logger.fatal("Luna could not be terminated gracefully!", e);
        }
    }

    /**
     * Runs the entire game loop including synchronization tasks.
     */
    private void loop() {
        try {
            // Do stuff from other threads.
            runSynchronizationTasks();

            // Run the main game loop.
            world.loop();
        } catch (Exception e) {
            logger.catching(e);
        }
    }

    /**
     * Runs all pending synchronization tasks in the backing queue. This allows other Threads to execute game logic
     * on the main game thread.
     */
    private void runSynchronizationTasks() {
        for (; ; ) {
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
     * Runs both synchronous and asynchronous logic from Kotlin scripts and waits for it to complete.
     *
     * @param eventFunction Produces the event message to pass.
     * @param waitingMessage The message to log while waiting for tasks to complete.
     */
    private <E extends Event> void runKotlinTasks(Function<AsyncExecutor, E> eventFunction, String waitingMessage) {
        AsyncExecutor executor = new AsyncExecutor(ThreadUtils.cpuCount(), "BackgroundLoaderThread");
        context.getPlugins().lazyPost(eventFunction.apply(executor)).forEach(Runnable::run);
        try {
            int count = executor.getTaskCount();
            if (count > 0) {
                logger.info(waitingMessage, box(count));
                executor.await(true);
            }
        } catch (ExecutionException e) {
            throw new CompletionException(e.getCause());
        }
    }

    /**
     * Performs a graceful shutdown of Luna. A shutdown performed in this way allows Luna to properly save resources before the
     * application exits. This method will block for as long as it needs to until all important threads have completed their tasks.
     * <p>
     * This function runs on the game thread, so players can be freely manipulated without synchronization.
     */
    private void gracefulShutdown() {
        var world = context.getWorld();
        var loginService = world.getLoginService();
        var logoutService = world.getLogoutService();

        // Run shutdown code from Kotlin scripts, and wait for the asynchronous portions to complete.
        runKotlinTasks(ServerShutdownEvent::new, "Waiting for {} Kotlin shutdown task(s) to complete...");

        // Run last minute game tasks from other threads.
        runSynchronizationTasks();

        // Will stop any current and future logins.
        loginService.stopAsync().awaitTerminated();

        // Save all players and wait for it to complete.
        getUnchecked(world.getPersistenceService().saveAll());

        // Synchronously disconnect all players.
        world.getPlayers().forEach(player -> {
            var disconnectFuture = player.getClient().disconnect();
            disconnectFuture.awaitUninterruptibly();
        });

        // Wait for the disconnected players to be saved.
        logoutService.stopAsync().awaitTerminated();

        // Close cache resource.
        context.getCache().close();

        // Wait for general-purpose tasks to complete.
        fastPool.shutdown();
        awaitTerminationUninterruptibly(fastPool);
    }

    /**
     * Schedules a system update in {@code ticks} amount of time.
     *
     * @param ticks The amount of ticks to schedule for.
     */
    public void scheduleSystemUpdate(int ticks) {
        // Preliminary save of all players.
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
     * Queues a task to be run on the game thread at the start of the next tick. This is useful when you are performing
     * work on another thread, but a portion still needs to be run on the game thread. For example
     * <pre>
     * {@code
     * // We're on the game thread.
     * GameService service = player.getService();
     * player.sendMessage("You will be awarded your prize within one minute if you qualify.");
     * service.submit(() -> {
     *      // Now we're in a pooled thread.
     *
     *      // Takes 5-10s to complete.
     *      Prize prize = Prize.slowlyRetrieveData(player.getUsername());
     *
     *      if(prize.isQualified()) {
     *          // This code needs to be run on the game thread to ensure thread safety.
     *          service.sync(() -> {
     *             player.sendMessage("You qualified! Your prize was sent to your bank!");
     *             player.getBank().addAll(prize.getItems());
     *          });
     *      } else {
     *          // This as well.
     *          service.sync(() -> player.sendMessage("You didn't qualify. Bummer."));
     *      }
     * });
     * }
     * </pre>
     * The same code can also be expressed more succinctly using {@link ListenableFuture} and {@link #gameExecutor}.
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
        return fastPool.submit(t);
    }

    /**
     * Runs a listening asynchronous task.  <strong>Warning: Tasks may not be ran right away, as there is a limit to
     * how large the backing pool can grow to. This is to prevent DOS type attacks.</strong> If you require a faster
     * pool for higher priority tasks, consider using a dedicated pool from {@link ExecutorUtils}.
     *
     * @param t The task to run.
     * @return The result of {@code t}.
     */
    public ListenableFuture<Void> submit(Runnable t) {
        return submit(() -> {
            t.run();
            return null;
        });
    }

    /**
     * @return The context instance.
     */
    public LunaContext getContext() {
        return context;
    }

    /**
     * @return The game executor. Any code passed through it will run on the game thread.
     */
    public Executor getExecutor() {
        return gameExecutor;
    }

    /**
     * @return The synchronizer for the Kotlin startup tasks.
     */
    public CountDownLatch getSynchronizer() {
        return synchronizer;
    }
}
