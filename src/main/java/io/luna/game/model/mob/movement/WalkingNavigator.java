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
import io.luna.game.model.path.InvalidPathException;
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
 * Handles low-level, optionally asynchronous movement for a {@link Mob}. This class provides two styles of movement:
 * <ul>
 *     <li><b>Stepping</b> - immediate single-tile steps that do not use a pathfinder.</li>
 *     <li><b>Path walking</b> - computes a path using a {@link GamePathfinder} and queues it into the mob's
 *     walking queue.</li>
 * </ul>
 * <p>
 * Pathfinding can be performed synchronously or asynchronously. When asynchronous, path computation is offloaded to
 * a lightweight, efficient, shared fork-join pool to avoid blocking the game thread.
 *
 * @author lare96
 */
public class WalkingNavigator {

    /**
     * The logger instance.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Shared pathfinding pool.
     * <p>
     * Uses 2 threads to limit CPU usage. Asynchronous-mode {@link ForkJoinPool} helps with throughput under load.
     */
    private static final ForkJoinPool pool = new ForkJoinPool(2, defaultForkJoinWorkerThreadFactory, null, true);

    /**
     * The collision manager.
     */
    private final CollisionManager collisionManager;

    /**
     * The owning mob.
     */
    private final Mob mob;

    private NavigationRequest active;


    /**
     * Creates a navigator for {@code mob}.
     *
     * @param mob The mob this navigator controls.
     */
    public WalkingNavigator(Mob mob) {
        this.mob = mob;
        collisionManager = mob.getWorld().getCollisionManager();
    }

    // must only be called from game thread to be thread safe
    public CompletableFuture<NavigationResult> submit(NavigationRequest request) {
        if (request.equals(active)) {
            return active.getPending();
        }
        if(isActive()) {
            cancel();
        }
        active = request;
        mob.submitAction(new NavigationAction(mob, active));
        return active.getPending();
    }

    // navigates on top, one-shot
    public CompletableFuture<NavigationResult> navigate(Position position, boolean async) {
        var request = NavigationRequest.builder(mob)
                .async(async)
                .continuous(false)
                .policy(new InteractionPolicy(InteractionType.SIZE, 0))
                .target(position)
                .build();
        return submit(request);
    }

    // navigates on top with explicit continuous flag
    public CompletableFuture<NavigationResult> navigate(Position position, boolean async, boolean continuous) {
        var request = NavigationRequest.builder(mob)
                .async(async)
                .continuous(continuous)
                .policy(new InteractionPolicy(InteractionType.SIZE, 0))
                .target(position)
                .build();
        return submit(request);
    }

    // navigates to interaction distance from offsetDir, continuous if navigating to mob
    public CompletableFuture<NavigationResult> navigate(Entity entity, Direction offsetDir, boolean async) {
        return navigate(entity, offsetDir, async, entity instanceof Mob);
    }

    // navigates to interaction distance from offsetDir with explicit continuous flag
    public CompletableFuture<NavigationResult> navigate(Entity entity, Direction offsetDir, boolean async, boolean continuous) {
        var request = NavigationRequest.builder(mob)
                .async(async)
                .continuous(continuous)
                .policy(new InteractionPolicy(InteractionType.SIZE, 1))
                .offsetDir(offsetDir)
                .target(entity)
                .build();
        return submit(request);
    }

    // navigate to interaction distance, facing the entity, continuous if navigating to mob
    public CompletableFuture<NavigationResult> navigate(Entity entity, boolean async) {
        return navigate(entity, null, async);
    }

    // navigate to interaction distance, facing the entity, with explicit continuous flag
    public CompletableFuture<NavigationResult> navigate(Entity entity, boolean async, boolean continuous) {
        return navigate(entity, null, async, continuous);
    }

    // navigate to interaction distance, behind the entity, always continuous
    public CompletableFuture<NavigationResult> navigateBehind(Mob mob, boolean async) {
        return navigate(mob, mob.getLastDirection().opposite(), async, true);
    }

    // navigate to interaction distance, behind the entity, with explicit continuous flag
    public CompletableFuture<NavigationResult> navigateBehind(Mob mob, boolean async, boolean continuous) {
        return navigate(mob, mob.getLastDirection().opposite(), async, continuous);
    }

    // navigate to interaction distance, ahead of the entity, always continuous
    public CompletableFuture<NavigationResult> navigateAhead(Mob mob, boolean async) {
        return navigate(mob, mob.getLastDirection(), async, true);
    }

    // navigate to interaction distance, ahead of the entity, with explicit continuous flag
    public CompletableFuture<NavigationResult> navigateAhead(Mob mob, boolean async, boolean continuous) {
        return navigate(mob, mob.getLastDirection(), async, continuous);
    }

    /**
     * Attempts to queue a single step in {@code direction}.
     * <p>
     * This does <b>not</b> invoke a pathfinder. It is a single-step "nudge" that only succeeds if the target tile
     * is traversable.
     *
     * @param direction The direction to step.
     * @return {@code true} if the step was queued, {@code false} if blocked.
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
     * @param includeDiagonals If {@code true}, considers 8 directions; otherwise only uses NESW.
     * @return {@code true} if a step was queued, {@code false} if all candidate directions were blocked.
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

    public void cancel() {
        if (isActive()) {
            active.getPending().cancel(true);
        }
    }

    public boolean isActive() {
        if (active != null) {
            return !active.getPending().isDone();
        }
        return false;
    }

    public Locatable getCurrentTarget() {
        if (isActive()) {
            return active.getTarget();
        }
        return null;
    }


    /**
     * Computes a path from {@code start} to {@code target}. If {@code async} is {@code true}, the path is
     * computed on {@link #pool}. Otherwise, computation happens immediately in the current thread.
     *
     * @param start The start position.
     * @param target The target position.
     * @param pathfinder The pathfinder to use.
     * @param async Whether to compute asynchronously.
     * @return A future containing a validated path (may be empty if blocked/unreachable).
     */
    public CompletableFuture<Deque<Position>> findPath(Position start, Position target,
                                                       GamePathfinder<Position> pathfinder, boolean async) {
        CompletableFuture<Deque<Position>> result = async
                ? CompletableFuture.supplyAsync(() -> pathfinder.find(start, target), pool)
                : CompletableFuture.completedFuture(pathfinder.find(start, target));
        result = result.thenApply(it -> {
            if (it == null && !start.equals(target)) {
                // Empty path, no valid route could be found.
                throw new InvalidPathException(mob, start, target, pathfinder);
            }
            return it;
        });
        return handleExceptions(target, result);
    }

    /**
     * Walks to {@code destination} using a specific {@code pathfinder}.
     *
     * @param destination The destination to walk to.
     * @param pathfinder The pathfinder implementation to use.
     * @param async If {@code true}, pathfinding is performed asynchronously.
     * @return A future that completes once the path has been computed and queued.
     */
    CompletableFuture<Void> walk(Locatable destination, GamePathfinder<Position> pathfinder, boolean async) {
        CompletableFuture<Void> result = findPath(mob.getPosition(), destination.abs(), pathfinder, async)
                .thenAcceptAsync(path -> mob.getWalking().replacePath(path), mob.getService().getGameExecutor());
        return handleExceptions(destination, result);
    }

    /**
     * Selects the default pathfinder to use for this mob.
     *
     * <ul>
     *     <li>{@link Bot} uses {@link BotPathfinder}.</li>
     *     <li>{@link Player} uses {@link PlayerPathfinder}.</li>
     *     <li>Other mobs use {@link SimplePathfinder}.</li>
     * </ul>
     *
     * @return The default pathfinder for the controlled mob.
     */
    GamePathfinder<Position> getDefaultPathfinder() {
        int plane = mob.getPosition().getZ();
        // todo pathfinder should be a part of the builder request. this is the default
        if (mob instanceof Bot) {
            return new BotPathfinder(collisionManager, plane);
        } else if (mob instanceof Player) {
            return new PlayerPathfinder(collisionManager, plane);
        } else {
            return new SimplePathfinder(collisionManager);
        }
    }

    /**
     * Wraps a future with logging and a fallback value when errors occur.
     * <p>
     * Cancellation is treated as normal control flow and is not logged.
     *
     * @param target The intended navigation target (used for logging context).
     * @param result The future to wrap.
     * @param <T> The future type.
     * @return A future that logs unexpected exceptions and returns {@code value} on failure.
     */
    <T> CompletableFuture<T> handleExceptions(Locatable target, CompletableFuture<T> result) {
        return result.exceptionally(ex -> {
            boolean ignored = ex instanceof CancellationException || ex instanceof InvalidPathException ||
                    ex.getCause() instanceof InvalidPathException;
            if (!ignored) {
                logger.error("Pathfinding for mob {} to target {} failed!", mob, target, ex);
            }
            return null;
        });
    }

    /**
     * Computes an adjacent offset position around {@code target} based on an approach direction.
     * <p>
     * This is used to find the tile this mob should occupy when moving next to a target without overlapping its bounds.
     * Both this mob's size and the target's size are taken into account, making it suitable for large NPCs and
     * other multi-tile entities.
     * <p>
     * If {@code offsetDir} is present, that direction is used directly. Otherwise, the direction is derived from this
     * mob's current position relative to the target.
     * <p>
     * The returned position is placed on the same height level as this mob.
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

        // Pick the "near edge" of the target rectangle based on approach direction.
        int targetX = dx <= 0 ? targetPosition.getX() : targetPosition.getX() + targetSizeX - 1;
        int targetY = dy <= 0 ? targetPosition.getY() : targetPosition.getY() + targetSizeY - 1;

        // Offset by this mob's size so we end up adjacent (not overlapping).
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
