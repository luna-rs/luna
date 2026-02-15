package io.luna.game.model.mob;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.path.BotPathfinder;
import io.luna.game.model.path.GamePathfinder;
import io.luna.game.model.path.PlayerPathfinder;
import io.luna.game.model.path.SimplePathfinder;
import io.luna.util.RandomUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
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
 * </p>
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
     * </p>
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

    /**
     * Creates a navigator for {@code mob}.
     *
     * @param mob The mob this navigator controls.
     */
    public WalkingNavigator(Mob mob) {
        this.mob = mob;
        collisionManager = mob.getWorld().getCollisionManager();
    }

    /**
     * Attempts to queue a single step in {@code direction}.
     * <p>
     * This does <b>not</b> invoke a pathfinder. It is a single-step "nudge" that only succeeds if the target tile
     * is traversable.
     * </p>
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

    /**
     * Walks to a tile adjacent to {@code target} suitable for interaction. This method chooses an adjacent
     * destination tile based on:
     * <ul>
     *     <li>The caller-provided {@code offsetDir}, or</li>
     *     <li>The direction between this mob and the target if no offset is provided.</li>
     * </ul>
     * The computed destination respects entity sizes (both the walking mob and the target). This is useful for
     * consistent "stand next to target" behavior for large NPCs/objects.
     * <p>
     * <b>Implementation note:</b> This method always uses {@link PlayerPathfinder}.
     * </p>
     *
     * @param target The entity to approach.
     * @param offsetDir Optional direction indicating which side of the target to approach from.
     * @param async If {@code true}, pathfinding is performed off-thread and queued back onto the game executor.
     * @return A future that completes once the path has been computed and queued (or completes normally with an empty
     * queue if no path is possible).
     */
    public CompletableFuture<Void> walkTo(Entity target, Optional<Direction> offsetDir, boolean async) {
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

        Position destination = new Position(targetX + offsetX, targetY + offsetY, height);
        return walk(destination, new PlayerPathfinder(collisionManager, height), async);
    }

    /**
     * Walks to {@code target} from the direction the target is currently facing. Commonly used to "stand in front of"
     * a mob (e.g., for certain interactions/behaviors).
     *
     * @param target The target mob to approach.
     * @param async If {@code true}, pathfinding is performed asynchronously.
     * @return A future that completes once the path has been computed and queued.
     */
    public CompletableFuture<Void> walkAhead(Mob target, boolean async) {
        return walkTo(target, Optional.of(target.getLastDirection()), async);
    }

    /**
     * Walks to {@code target} from behind (opposite of the target's last facing direction).
     *
     * @param target The target mob to approach.
     * @param async If {@code true}, pathfinding is performed asynchronously.
     * @return A future that completes once the path has been computed and queued.
     */
    public CompletableFuture<Void> walkBehind(Mob target, boolean async) {
        return walkTo(target, Optional.of(target.getLastDirection().opposite()), async);
    }

    /**
     * Walks to {@code destination} using the navigator's default pathfinder for this mob type.
     *
     * @param destination The destination to walk to.
     * @param async If {@code true}, pathfinding is performed asynchronously.
     * @return A future that completes once the path has been computed and queued.
     */
    public CompletableFuture<Void> walk(Locatable destination, boolean async) {
        return walk(destination, getPathfinder(), async);
    }

    /**
     * Walks to {@code destination} using a specific {@code pathfinder}.
     *
     * @param destination The destination to walk to.
     * @param pathfinder The pathfinder implementation to use.
     * @param async If {@code true}, pathfinding is performed asynchronously.
     * @return A future that completes once the path has been computed and queued.
     */
    public CompletableFuture<Void> walk(Locatable destination, GamePathfinder<Position> pathfinder, boolean async) {
        CompletableFuture<Void> result = findPath(mob.getPosition(), destination.absLocation(), pathfinder, async)
                .thenAcceptAsync(path -> mob.getWalking().addPath(path), mob.getService().getGameExecutor());
        return handleExceptions(destination, result, null);
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

        return handleExceptions(target, result, new ArrayDeque<>(0));
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
    private GamePathfinder<Position> getPathfinder() {
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
     * Wraps a future with logging and a fallback value when errors occur.
     *
     * <p>Cancellation is treated as normal control flow and is not logged.</p>
     *
     * @param target The intended navigation target (used for logging context).
     * @param result The future to wrap.
     * @param value The fallback value to return on failure.
     * @param <T> The future type.
     * @return A future that logs unexpected exceptions and returns {@code value} on failure.
     */
    private <T> CompletableFuture<T> handleExceptions(Locatable target, CompletableFuture<T> result, T value) {
        return result.exceptionally(ex -> {
            if (!(ex instanceof CancellationException)) {
                logger.error("Pathfinding for mob {} to target {} failed!", mob, target, ex);
            }
            return value;
        });
    }
}
