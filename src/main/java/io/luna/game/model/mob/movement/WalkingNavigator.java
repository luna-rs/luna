package io.luna.game.model.mob.movement;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.mob.interact.InteractionType;
import io.luna.game.model.path.BotPathfinder;
import io.luna.game.model.path.GamePathfinder;
import io.luna.game.model.path.PlayerPathfinder;
import io.luna.game.model.path.SimplePathfinder;
import io.luna.util.RandomUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

import static java.util.concurrent.ForkJoinPool.defaultForkJoinWorkerThreadFactory;

/**
 * Handles walking, stepping, and path-based navigation for a {@link Mob}.
 * <p>
 * The navigator supports immediate single-tile steps, direct path walking, and higher-level {@link NavigationRequest}
 * processing. Pathfinding may be performed synchronously on the calling thread or asynchronously on a shared pathfinding pool.
 * <p>
 * Navigation requests are submitted through {@link #submit(NavigationRequest)} and completed through the request's
 * pending {@link CompletableFuture}. Direct walking helpers are also provided for simple position and entity
 * movement.
 *
 * @author lare96
 */
public class WalkingNavigator {

    /**
     * The logger instance.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The shared worker pool used for asynchronous pathfinding.
     * <p>
     * This pool is intentionally small to avoid letting pathfinding consume too much CPU time under load.
     */
    private static final ForkJoinPool pool = new ForkJoinPool(2, defaultForkJoinWorkerThreadFactory, null, true);

    /**
     * The collision manager used for step validation and pathfinding.
     */
    private final CollisionManager collisionManager;

    /**
     * The mob controlled by this navigator.
     */
    private final Mob mob;

    /**
     * The currently active navigation request.
     * <p>
     * A request remains active until its pending future is completed or cancelled.
     */
    private NavigationRequest active;

    /**
     * Creates a new walking navigator for a mob.
     *
     * @param mob The mob this navigator controls.
     */
    public WalkingNavigator(Mob mob) {
        this.mob = mob;
        collisionManager = mob.getWorld().getCollisionManager();
    }

    /**
     * Submits a navigation request for this mob.
     * <p>
     * If the supplied request matches the currently active request, the existing pending result is returned instead
     * of submitting duplicate navigation work. If another request is already active, it is cancelled before the new
     * request is started.
     * <p>
     * This method should only be called from the game thread, because it mutates the active request and submits
     * movement actions.
     *
     * @param request The navigation request to submit.
     * @return The pending navigation result.
     */
    public CompletableFuture<NavigationResult> submit(NavigationRequest request) {
        if (request.equals(active)) {
            return active.getPending();
        }
        if (isActive()) {
            cancel();
        }
        active = request;
        mob.submitAction(new NavigationAction(mob, active));
        return active.getPending();
    }

    /**
     * Navigates this mob directly onto a position.
     * <p>
     * This is a one-shot request that completes once the mob reaches the exact tile or fails to reach it.
     *
     * @param position The exact position to navigate to.
     * @param async {@code true} to compute the path asynchronously, otherwise {@code false}.
     * @return The pending navigation result.
     */
    public CompletableFuture<NavigationResult> navigate(Position position, boolean async) {
        var request = NavigationRequest.builder(mob)
                .async(async)
                .continuous(false)
                .policy(new InteractionPolicy(InteractionType.SIZE, 0))
                .target(position)
                .build();
        return submit(request);
    }

    /**
     * Navigates this mob to interaction distance from an entity using an optional offset direction.
     * <p>
     * Mob targets are tracked continuously by default, while non-mob entity targets are treated as one-shot
     * navigation requests.
     *
     * @param entity The entity to navigate toward.
     * @param offsetDir The optional offset direction around the entity, or {@code null} to choose automatically.
     * @param async {@code true} to compute the path asynchronously, otherwise {@code false}.
     * @return The pending navigation result.
     */
    public CompletableFuture<NavigationResult> navigate(Entity entity, Direction offsetDir, boolean async) {
        return navigate(entity, offsetDir, async, entity instanceof Mob);
    }

    /**
     * Navigates this mob to interaction distance from an entity using an optional offset direction.
     *
     * @param entity The entity to navigate toward.
     * @param offsetDir The optional offset direction around the entity, or {@code null} to choose automatically.
     * @param async {@code true} to compute the path asynchronously, otherwise {@code false}.
     * @param continuous {@code true} to keep tracking the entity, otherwise {@code false}.
     * @return The pending navigation result.
     */
    public CompletableFuture<NavigationResult> navigate(Entity entity, Direction offsetDir, boolean async,
                                                        boolean continuous) {
        var request = NavigationRequest.builder(mob)
                .async(async)
                .continuous(continuous)
                .policy(new InteractionPolicy(InteractionType.SIZE, 1))
                .offsetDir(offsetDir)
                .target(entity)
                .build();
        return submit(request);
    }

    /**
     * Navigates this mob to interaction distance from an entity.
     * <p>
     * Mob targets are tracked continuously by default, while non-mob entity targets are treated as one-shot
     * navigation requests.
     *
     * @param entity The entity to navigate toward.
     * @param async {@code true} to compute the path asynchronously, otherwise {@code false}.
     * @return The pending navigation result.
     */
    public CompletableFuture<NavigationResult> navigate(Entity entity, boolean async) {
        return navigate(entity, null, async);
    }

    /**
     * Navigates this mob to interaction distance from an entity.
     *
     * @param entity The entity to navigate toward.
     * @param async {@code true} to compute the path asynchronously, otherwise {@code false}.
     * @param continuous {@code true} to keep tracking the entity, otherwise {@code false}.
     * @return The pending navigation result.
     */
    public CompletableFuture<NavigationResult> navigate(Entity entity, boolean async, boolean continuous) {
        return navigate(entity, null, async, continuous);
    }

    /**
     * Navigates this mob behind another mob.
     * <p>
     * The destination is based on the target mob's last facing direction. This request is continuous by default.
     *
     * @param mob The mob to navigate behind.
     * @param async {@code true} to compute the path asynchronously, otherwise {@code false}.
     * @return The pending navigation result.
     */
    public CompletableFuture<NavigationResult> follow(Mob mob, boolean async) {
        return navigateBehind(mob, async, true);
    }

    /**
     * Navigates this mob behind another mob.
     *
     * @param mob The mob to navigate behind.
     * @param async {@code true} to compute the path asynchronously, otherwise {@code false}.
     * @param continuous {@code true} to keep tracking the mob, otherwise {@code false}.
     * @return The pending navigation result.
     */
    public CompletableFuture<NavigationResult> navigateBehind(Mob mob, boolean async, boolean continuous) {
        return navigate(mob, mob.getLastDirection().opposite(), async, continuous);
    }

    /**
     * Navigates this mob ahead of another mob.
     * <p>
     * The destination is based on the target mob's last facing direction. This request is continuous by default.
     *
     * @param mob The mob to navigate ahead of.
     * @param async {@code true} to compute the path asynchronously, otherwise {@code false}.
     * @return The pending navigation result.
     */
    public CompletableFuture<NavigationResult> navigateAhead(Mob mob, boolean async) {
        return navigateAhead(mob, async, true);
    }

    /**
     * Navigates this mob ahead of another mob.
     *
     * @param mob The mob to navigate ahead of.
     * @param async {@code true} to compute the path asynchronously, otherwise {@code false}.
     * @param continuous {@code true} to keep tracking the mob, otherwise {@code false}.
     * @return The pending navigation result.
     */
    public CompletableFuture<NavigationResult> navigateAhead(Mob mob, boolean async, boolean continuous) {
        return navigate(mob, mob.getLastDirection(), async, continuous);
    }

    /**
     * Attempts to queue a single step in a direction.
     * <p>
     * This does not invoke a pathfinder. It is a single-step nudge that only succeeds if the destination tile is
     * traversable.
     *
     * @param direction The direction to step.
     * @return {@code true} if the step was queued, otherwise {@code false}.
     */
    public boolean step(Direction direction) {
        if (direction != Direction.NONE && collisionManager.traversable(mob.getPosition(), mob.getType(), direction)) {
            mob.getWalking().addStep(direction);
            return true;
        }
        return false;
    }

    /**
     * Attempts to queue a single random step.
     *
     * @param includeDiagonals {@code true} to include diagonal directions, otherwise {@code false}.
     * @return {@code true} if a random step was queued, otherwise {@code false}.
     */
    public boolean stepRandom(boolean includeDiagonals) {
        ImmutableList<Direction> directions = includeDiagonals ? Direction.ALL_EXCEPT_NONE : Direction.NESW;
        List<Direction> selectFrom = new ArrayList<>(directions.size());
        for (Direction next : directions) {
            if (collisionManager.traversable(mob.getPosition(), mob.getType(), next)) {
                selectFrom.add(next);
            }
        }
        if (selectFrom.isEmpty()) {
            return false;
        }
        mob.getWalking().addStep(RandomUtils.random(selectFrom));
        return true;
    }

    /**
     * Cancels the currently active navigation request, if one exists.
     */
    public void cancel() {
        if (isActive()) {
            active.getPending().cancel(true);
        }
    }

    /**
     * Determines whether this navigator currently has an active request.
     *
     * @return {@code true} if a navigation request is still pending, otherwise {@code false}.
     */
    public boolean isActive() {
        if (active != null) {
            return !active.getPending().isDone();
        }
        return false;
    }

    /**
     * @return The current navigation target, or {@code null} if no request is active.
     */
    public Locatable getCurrentTarget() {
        if (isActive()) {
            return active.getTarget();
        }
        return null;
    }

    /**
     * Computes a path from a start position to a target position.
     * <p>
     * If {@code async} is {@code true}, pathfinding is performed on the shared pathfinding pool. Otherwise, pathfinding
     * is performed immediately on the calling thread.
     *
     * @param start The start position.
     * @param target The target position.
     * @param pathfinder The pathfinder to use.
     * @param async {@code true} to compute asynchronously, otherwise {@code false}.
     * @return A future containing the computed path, or {@code null} if no valid path was found.
     */
    public CompletableFuture<Deque<Position>> findPath(Position start, Position target,
                                                       GamePathfinder<Position> pathfinder, boolean async) {
        CompletableFuture<Deque<Position>> result = async
                ? CompletableFuture.supplyAsync(() -> pathfinder.find(start, target), pool)
                : CompletableFuture.completedFuture(pathfinder.find(start, target));
        result = result.thenApply(it -> {
            if (it == null && !start.equals(target)) {
                return null;
            }
            return it;
        });
        return handleExceptions(target, result);
    }

    /**
     * Computes and queues a path to a destination.
     * <p>
     * The path is computed using the supplied pathfinder, then applied to the mob's walking queue on the game
     * executor.
     *
     * @param destination The destination to walk to.
     * @param pathfinder The pathfinder implementation to use.
     * @param async {@code true} to perform pathfinding asynchronously, otherwise {@code false}.
     * @return A future that completes once the path has been computed and queued.
     */
    CompletableFuture<Void> walk(Locatable destination, GamePathfinder<Position> pathfinder, boolean async) {
        CompletableFuture<Void> result = findPath(mob.getPosition(), destination.abs(), pathfinder, async)
                .thenAcceptAsync(path -> mob.getWalking().replacePath(path), mob.getService().getGameExecutor());
        return handleExceptions(destination, result);
    }

    /**
     * Selects the default pathfinder for this mob.
     * <ul>
     *     <li>{@link Bot} mobs use {@link BotPathfinder}.</li>
     *     <li>{@link Player} mobs use {@link PlayerPathfinder}.</li>
     *     <li>All other mobs use {@link SimplePathfinder}.</li>
     * </ul>
     *
     * @return The default pathfinder for this mob.
     */
    GamePathfinder<Position> getDefaultPathfinder() {
        int plane = mob.getPosition().getZ();
        if (mob instanceof Bot) {
            return new BotPathfinder(collisionManager, plane);
        } else if (mob instanceof Player) {
            return new PlayerPathfinder(collisionManager, plane);
        } else {
            return new SimplePathfinder(collisionManager);
        }
    }

    /**
     * Adds common exception handling to a pathfinding future.
     * <p>
     * Cancellation is treated as normal control flow and is not logged. Unexpected failures are logged and converted
     * into {@code null} completion values.
     *
     * @param target The intended navigation target used for logging context.
     * @param result The future to wrap.
     * @param <T> The future result type.
     * @return A future that logs unexpected failures and returns {@code null} when recovery is needed.
     */
    <T> CompletableFuture<T> handleExceptions(Locatable target, CompletableFuture<T> result) {
        return result.exceptionally(ex -> {
            boolean ignored = ex instanceof CancellationException;
            if (!ignored) {
                logger.error("Pathfinding for mob {} to target {} failed!", mob, target, ex);
            }
            return null;
        });
    }

    /**
     * Computes an adjacent offset position around an entity.
     * <p>
     * This is used when a mob needs to walk next to an entity instead of walking directly onto its tile. Both this
     * mob's size and the target entity's size are considered, making this suitable for large NPCs and other
     * multi-tile entities.
     * <p>
     * If {@code offsetDir} is present, that direction is used directly. Otherwise, the direction is derived from this
     * mob's current position relative to the target.
     *
     * @param target The target entity to compute an adjacent position around.
     * @param offsetDir The optional direction to approach from.
     * @return The computed adjacent offset position.
     */
    Position computeOffsetPosition(Entity target, Optional<Direction> offsetDir) {
        int sizeX = mob.sizeX();
        int sizeY = mob.sizeY();

        int targetSizeX = target.sizeX();
        int targetSizeY = target.sizeY();

        Position position = mob.getPosition();
        int height = position.getZ();
        Position targetPosition = target.getPosition();

        Direction direction = offsetDir.orElse(Direction.between(position, targetPosition));
        int dx = direction.getTranslateX();
        int dy = direction.getTranslateY();

        int targetX = dx <= 0 ? targetPosition.getX() : targetPosition.getX() + targetSizeX - 1;
        int targetY = dy <= 0 ? targetPosition.getY() : targetPosition.getY() + targetSizeY - 1;

        int offsetX;
        if (dx < 0) {
            offsetX = -sizeX;
        } else if (dx > 0) {
            offsetX = 1;
        } else {
            offsetX = 0;
        }

        int offsetY;
        if (dy < 0) {
            offsetY = -sizeY;
        } else if (dy > 0) {
            offsetY = 1;
        } else {
            offsetY = 0;
        }
        return new Position(targetX + offsetX, targetY + offsetY, height);
    }
}