package io.luna.game;

import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.luna.LunaContext;
import io.luna.game.model.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A service that handles game logic processing.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class GameService extends AbstractScheduledService {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * A cached thread pool for low-priority tasks.
     */
    private final ListeningExecutorService executorService = MoreExecutors.listeningDecorator(
        Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("LunaWorkerThread").build()));

    /**
     * A queue of tasks to run.
     */
    private final Queue<Runnable> syncTasks = new ConcurrentLinkedQueue<>();

    /**
     * The context instance.
     */
    private final LunaContext context;

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
    protected void runOneIteration() throws Exception {
        try {
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

            World world = context.getWorld();
            world.dequeueLogins();
            world.runGameLoop();
            world.dequeueLogouts();
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
            /* TODO needs to be fixed, tested, and debugged */
            World world = context.getWorld();

            LOGGER.fatal("The asynchronous game service has been shutdown, exiting...");
            syncTasks.forEach(Runnable::run);
            syncTasks.clear();
            world.getPlayers().clear();
            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (Exception e) {
            LOGGER.catching(e);
        }
        System.exit(0);
    }

    /**
     * Queues a task to be ran on the next tick.
     */
    public void sync(Runnable t) {
        syncTasks.add(t);
    }

    /**
     * Runs an asynchronous task.
     */
    public void execute(Runnable t) {
        executorService.execute(t);
    }

    /**
     * Runs a result-bearing and listening asynchronous task.
     */
    public <T> ListenableFuture<T> submit(Callable<T> t) {
        return executorService.submit(t);
    }

    /**
     * Runs a listening asynchronous task.
     */
    public ListenableFuture<?> submit(Runnable t) {
        return executorService.submit(t);
    }

    /**
     * @return The context instance.
     */
    public LunaContext getContext() {
        return context;
    }
}
