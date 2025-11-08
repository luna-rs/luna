package io.luna.game.model.mob.bot.movement;

import io.luna.game.model.mob.bot.Bot;

import java.util.concurrent.ForkJoinPool;

import static java.util.concurrent.ForkJoinPool.defaultForkJoinWorkerThreadFactory;

/**
 * A manager responsible for scheduling asynchronous pathfinding and movement-related computations
 * for all {@link Bot} instances.
 * <p>
 * This class maintains a dedicated {@link ForkJoinPool} optimized for pathfinding calculations. Using a shared pool
 * avoids blocking the main game logic thread while allowing multiple bots to compute paths concurrently.
 * </p>
 *
 * @author lare96
 */
public final class BotMovementManager {

    /**
     * The shared pathfinding pool. Uses as many threads as there are available CPU cores,
     * created in asynchronous mode for better parallelism under load.
     */
    private final ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors(),
            defaultForkJoinWorkerThreadFactory, null, true);

    /**
     * Returns the shared pathfinding pool.
     * <p>
     * This pool is intended for all asynchronous movement-related computations,
     * including {@code walking.findPath(...)} calls inside {@link BotMovementStack}.
     * </p>
     *
     * @return The {@link ForkJoinPool} used for asynchronous pathfinding.
     */
    public ForkJoinPool getPool() {
        return pool;
    }
}
