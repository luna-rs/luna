package io.luna.game.model.mob.bot.movement;

import io.luna.game.GameService;
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
import io.luna.game.model.mob.WalkingQueue;
import io.luna.game.model.mob.bot.Bot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * A movement controller that manages sequential pathfinding and movement requests.
 * <p>
 * It encapsulates a single active {@link BotMovementRequest}, representing the current movement operation the bot
 * is performing. When a new request is issued via {@link #addPath(Locatable)}, any existing request is
 * automatically cancelled and replaced.
 * </p>
 *
 * <p>
 * Pathfinding is performed asynchronously on the thread pool managed by {@link BotMovementManager#getPool()}, while
 * movement application occurs on the game logic thread provided by {@link GameService#getGameExecutor()}.
 * This separation ensures pathfinding does not block the main tick loop.
 * </p>
 *
 * @author lare96
 */
public final class BotMovementStack {

    /**
     * The logger.
     */
    private static Logger logger = LogManager.getLogger();

    /**
     * Internal structure representing a single movement request in progress.
     * <p>
     * Contains both the {@link CompletableFuture} tracking the pathfinding completion and the {@link Locatable}
     * target being walked to.
     * </p>
     */
    private static final class BotMovementRequest {

        /**
         * The future representing the asynchronous path generation or movement process.
         */
        private final CompletableFuture<Void> result;

        /**
         * The destination being targeted by this movement.
         */
        private final Locatable target;

        /**
         * Creates a new movement request.
         *
         * @param result The future representing this request.
         * @param target The destination target.
         */
        private BotMovementRequest(CompletableFuture<Void> result, Locatable target) {
            this.result = result;
            this.target = target;
        }
    }

    /**
     * The bot.
     */
    private final Bot bot;

    /**
     * The movement manager that schedules async pathfinding operations.
     */
    private final BotMovementManager manager;

    /**
     * The currently active movement request, or {@code null} if idle.
     */
    private BotMovementRequest request;

    /**
     * Creates a new {@link BotMovementStack}.
     *
     * @param bot The bot.
     * @param manager The movement manager coordinating async pathfinding.
     */
    public BotMovementStack(Bot bot, BotMovementManager manager) {
        this.bot = bot;
        this.manager = manager;
    }

    /**
     * Begins an asynchronous walk operation toward the given target. If another movement is already in progress, it
     * is cancelled and cleared before the new one begins.
     *
     * <p>
     * Pathfinding is executed asynchronously using the movement manager's pool, and once complete, the resulting
     * path is applied on the game thread.
     * </p>
     *
     * @param target The target destination to walk toward.
     * @return A {@link CompletableFuture} that completes when the path is generated and queued, not when the bot
     * arrives at the destination. The returned future's default actions will run on the game thread.
     */
    public CompletableFuture<Void> addPath(Locatable target) {
        WalkingQueue walking = bot.getWalking();
        if (request != null && !request.result.isDone()) {
            if (request.target.equals(target)) {
                // Duplicate request.
                return request.result;
            }
            // Cancel existing request before we start a new one.
            request.result.cancel(false);
            walking.clear();
            bot.log("Cancelling existing movement " + request.target + ".");
        }
        // Generate async path and apply it when ready.
        Position position = bot.getPosition();
        request = new BotMovementRequest(
                CompletableFuture.supplyAsync(() -> walking.findPath(position, target), manager.getPool()).
                        thenAcceptAsync(path -> {
                            walking.addPath(path);
                            bot.log("Path generated, now walking" + target + ".");
                        }, bot.getService().getGameExecutor()).
                        exceptionally(ex -> {
                            if (!(ex instanceof CancellationException)) {
                                logger.error("Pathfinding for bot {} and {} failed!", bot.getUsername(), target, ex);
                            }
                            return null;
                        }),
                target);
        bot.log("Generating new movement path " + target + ".");
        return request.result;
    }
}
