package io.luna.game.model.mob.bot.movement;

import io.luna.game.GameService;
import io.luna.game.model.Locatable;
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
 * Pathfinding is performed asynchronously on the thread pool managed by {@link BotMovementManager#getPool()},
 * while movement application occurs on the game logic thread provided by {@link GameService#getGameExecutor()}.
 * This separation ensures pathfinding does not block the main tick loop.
 * </p>
 *
 * @author lare96
 */
public final class BotMovementStack {

    /**
     * Logger for movement-related debugging and error reporting.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Internal structure representing a single movement request in progress.
     * <p>
     * Contains both the {@link CompletableFuture} tracking the pathfinding completion and the {@link Locatable}
     * target being walked to.
     * </p>
     */
    private static final class BotMovementRequest {

        /**
         * The future representing the asynchronous path generation and/or movement task.
         * <p>
         * This future completes when the generated path has been queued into the bot’s {@link WalkingQueue},
         * not when the bot physically arrives at the destination.
         * </p>
         */
        private final CompletableFuture<Void> result;

        /**
         * The destination this movement request is trying to reach.
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
     * The bot executing the movement operations.
     */
    private final Bot bot;

    /**
     * Manager responsible for dispatching async pathfinding tasks.
     */
    private final BotMovementManager manager;

    /**
     * The current pending movement request, or {@code null} if no movement task is active.
     */
    private BotMovementRequest request;

    /**
     * Creates a new {@link BotMovementStack}.
     *
     * @param bot The bot associated with this stack.
     * @param manager The manager coordinating asynchronous pathfinding operations.
     */
    public BotMovementStack(Bot bot, BotMovementManager manager) {
        this.bot = bot;
        this.manager = manager;
    }

    /**
     * Begins an asynchronous walk operation toward the given target. If another movement is already in progress,
     * it is cancelled and cleared before the new one begins.
     * <p>
     * Pathfinding is executed asynchronously using the movement manager's pool, and once complete, the resulting
     * path is applied on the game thread.
     * </p>
     *
     * @param target The target destination to walk toward.
     * @return A {@link CompletableFuture} completing when the path has been generated and queued.
     *         The returned future does <strong>not</strong> wait for arrival at the destination.
     */
    public CompletableFuture<Void> addPath(Locatable target) {
        if (isCurrentTarget(target)) {
            return request.result;
        }
        cancel();
        request = createRequest(target);
        return request.result;
    }

    /**
     * Determines whether the bot is already moving toward the specified target.
     *
     * @param target The destination to test against the active request.
     * @return {@code true} if there is an active request and its destination equals {@code target}.
     */
    public boolean isCurrentTarget(Locatable target) {
        return isActive() && request.target.equals(target);
    }

    /**
     * Returns whether a movement request is currently active and not completed or cancelled.
     *
     * @return {@code true} if a movement request is in progress.
     */
    public boolean isActive() {
        return request != null && !request.result.isDone();
    }

    /**
     * Cancels the currently active movement request, if one exists.
     * <p>
     * Cancelling a request stops the bot's walking queue immediately and logs the cancellation.
     * </p>
     */
    public void cancel() {
        if (isActive()) {
            request.result.cancel(false);
            bot.getWalking().clear();
            bot.log("Cancelling existing movement " + request.target + ".");
        }
    }

    /**
     * Creates a new asynchronous movement request toward the given target.
     * <p>
     * The path is generated off-thread using the movement pool, then submitted back to the game thread for
     * execution. Any exceptions besides cancellation are logged.
     * </p>
     *
     * @param target The movement destination.
     * @return A constructed {@link BotMovementRequest} wrapping the future and target.
     */
    private BotMovementRequest createRequest(Locatable target) {
        WalkingQueue walking = bot.getWalking();

        bot.log("Generating new movement path to " + target + ".");
        CompletableFuture<Void> pathFuture =
                CompletableFuture.supplyAsync(() -> walking.findPath(target, true), manager.getPool())
                        .thenAcceptAsync(path -> {
                            walking.addPath(path);
                            bot.log("Path generated, now walking to " + target + ".");
                        }, bot.getService().getGameExecutor())
                        .exceptionally(ex -> {
                            if (!(ex instanceof CancellationException)) {
                                logger.error("Pathfinding for bot {} and {} failed!",
                                        bot.getUsername(), target, ex);
                            }
                            return null;
                        });

        return new BotMovementRequest(pathFuture, target);
    }

    /**
     * Returns the bot associated with this movement stack.
     *
     * @return The bot instance.
     */
    public Bot getBot() {
        return bot;
    }
}
