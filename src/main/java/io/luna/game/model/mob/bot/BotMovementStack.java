package io.luna.game.model.mob.bot;

import io.luna.game.GameService;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityState;
import io.luna.game.model.Locatable;
import io.luna.game.model.mob.WalkingNavigator;
import io.luna.game.model.mob.WalkingQueue;
import io.luna.game.task.Task;

import java.util.concurrent.CompletableFuture;

/**
 * A movement controller that manages sequential pathfinding and movement requests.
 * <p>
 * It encapsulates a single active {@link BotMovementRequest}, representing the current movement operation the bot
 * is performing. When a new request is issued via {@link #walk(Locatable)}, any existing request is
 * automatically cancelled and replaced.
 * </p>
 * <p>
 * Pathfinding is performed asynchronously on the thread pool managed by {@link WalkingNavigator}, while movement
 * application occurs on the game logic thread provided by {@link GameService#getGameExecutor()}. This separation
 * ensures pathfinding does not block the main tick loop.
 * </p>
 *
 * @author lare96
 */
public final class BotMovementStack {

    /*
     * TODO Webwalking support:
     *  - Integrate stairs, doors, ladders, trapdoors, and other interactive world objects into BotPathfinder.
     *  - Add a registration/metadata layer for these objects so the pathfinder can treat them as navigable edges
     *    (e.g., open door, climb ladder, use trapdoor) instead of only relying on raw collision checks.
     *  - Ensure bots automatically discover and use these interactions when computing paths to dynamic targets.
     *
     * TODO Movement priorities:
     *  - NORMAL: Default movement priority. Will be ignored if current request is HIGH/IMMUTABLE.
     *  - HIGH: Will ignore NORMAL requests for cancellation. Overridden by other HIGH level requests and IMMUTABLE.
     *  - IMMUTABLE: Once requested, can never be overridden, even by another IMMUTABLE request.
     */

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
     * The current pending movement request, or {@code null} if no movement task is active.
     */
    private BotMovementRequest request;

    /**
     * Creates a new {@link BotMovementStack}.
     *
     * @param bot The bot associated with this stack.
     */
    public BotMovementStack(Bot bot) {
        this.bot = bot;
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
     * @return A {@link CompletableFuture} completing when the path has been generated and queued. The returned future
     * does <strong>not</strong> wait for arrival at the destination.
     */
    public CompletableFuture<Void> walk(Locatable target) {
        if (isCurrentTarget(target)) {
            return request.result;
        }
        cancel();
        request = createRequest(target);
        return request.result;
    }

    /**
     * Begins an asynchronous walk operation toward the given target and returns a future that completes once the bot
     * stops moving and is either within interaction distance of the target or has failed to reach it.
     * <p>
     * This method first queues a path via {@link #walk(Locatable)} and then schedules a lightweight polling task on the
     * game thread to detect when the bot's {@link WalkingQueue} becomes empty. Once movement ends, the future completes
     * {@code true} if the bot is within the target's effective size distance, otherwise {@code false}.
     * </p>
     *
     * @param target The target destination to walk toward.
     * @return A {@link CompletableFuture} that completes {@code true} if the bot ends within reach of {@code target},
     * or {@code false} if movement ends elsewhere or the bot becomes inactive.
     */
    public CompletableFuture<Boolean> walkUntilReached(Locatable target) {
        // TODO If priorities are ever added this needs to be changed to ensure its listening for the right target.
        CompletableFuture<Boolean> walkResult = new CompletableFuture<>();
        walk(target).thenAcceptAsync(it ->
                bot.getWorld().schedule(new Task(true, 1) {
                    @Override
                    protected void execute() {
                        if (bot.getState() == EntityState.INACTIVE) {
                            cancel();
                            return;
                        }
                        if (bot.getWalking().isEmpty()) {
                            int size = 1;
                            if (target instanceof Entity) {
                                size = ((Entity) target).size();
                            }
                            if (bot.getPosition().isWithinDistance(target.absLocation(), size)) {
                                walkResult.complete(true);
                            } else {
                                walkResult.complete(false);
                            }
                            cancel();
                        }
                    }
                }), bot.getService().getGameExecutor());
        return walkResult;
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
        bot.log("Generating new movement path to " + target + ".");
        return new BotMovementRequest(bot.getNavigator().walk(target, true), target);
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
