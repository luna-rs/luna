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
import io.luna.game.model.object.GameObject;
import io.luna.game.model.path.GamePathfinder;
import io.luna.game.model.path.PathResult;
import io.luna.game.model.path.PathResultType;
import io.luna.util.RandomUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    // TODO@0.5.0 The builder should let you "test" the request to see if its valid (generates a valid path).
    //  but if the builder is used this way, it passes that pre-computed path onto navigationaction. disabled for
    //  continuous actions.
    //  Flow could look like var request = builder.async(true).target(other)
    //    if(request.test()) { // equivalent to canNavigate (result cached, path generated cached, always true for continuous)
    //        result.complete() // equivalent to navigate()/submit() (uses generated path on non-continuous requests)
    //    }
    //  While the normal utility methods remain unchanged.
    //  Should save a TON of resources in situations where lots of bots are using the travel system.

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
     * The farthest, in tiles, that a mob may have moved from where a path was computed for the path to still be
     * applied. A mob that walked on while the path was being computed is still close enough for the walking queue to
     * splice the path in, but one that was teleported is not.
     */
    private static final int MAX_PATH_DRIFT = 4;

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
     * The generation of the most recent path request. A path is only applied if no newer request has been made, and
     * nothing has been cancelled, since it was requested. Cancelling the future returned by {@link #walk} does not stop
     * work that is already in flight, so this is what keeps stale paths from being applied.
     */
    private final AtomicInteger pathGeneration = new AtomicInteger();

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
        // A completed result describes an earlier attempt, not this retry's current reachability.
        if (isActive() && request.equals(active)) {
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
     * The destination is based on the target mob's last facing direction. This request is continuous and synchronous
     * by default.
     *
     * @param mob The mob to navigate behind.
     * @return The pending navigation result.
     */
    public CompletableFuture<NavigationResult> follow(Mob mob) {
        return navigateBehind(mob, false, true);
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
        if (direction != Direction.NONE &&
                collisionManager.traversable(mob.getPosition(), mob.getType(), direction, mob.size(),
                        mob.getRouteStrategy())) {
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
            if (collisionManager.traversable(mob.getPosition(), mob.getType(), next, mob.size(),
                    mob.getRouteStrategy())) {
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
        discardPaths();
        if (isActive()) {
            active.getPending().cancel(true);
        }
    }

    /**
     * Discards the paths that are still being computed, so that they are not applied once they are done.
     */
    void discardPaths() {
        pathGeneration.incrementAndGet();
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
     * @return {@code true} if there is currently active continuous navigation in-progress.
     */
    public boolean isCurrentContinuous() {
        if (isActive()) {
            return !active.getPending().isDone() && active.isContinuous();
        }
        return false;
    }

    /**
     * @return The pending navigation result, or {@code null} if no request is active.
     */
    public CompletableFuture<NavigationResult> getCurrentPending() {
        if (isActive()) {
            return active.getPending();
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
        CompletableFuture<PathResult<Position>> pathResultFuture = async
                ? CompletableFuture.supplyAsync(() -> pathfinder.find(start, target), pool)
                : CompletableFuture.completedFuture(pathfinder.find(start, target));

        CompletableFuture<Deque<Position>> pathFuture = pathResultFuture.thenApply(it -> {
            if (it.getType() == PathResultType.FAILED) {
                return null;
            }
            return it;
        }).thenApply(it -> {
            if (it != null) {
                return it.getPath();
            }
            return null;
        });
        pathFuture.whenComplete((path, error) -> {
            if (pathFuture.isCancelled()) {
                // Prevent queued searches from starting. Running searches are not interrupted by CompletableFuture.
                pathResultFuture.cancel(false);
            }
        });
        return handleExceptions(target, pathFuture);
    }

    /**
     * Computes and queues a path to a destination.
     * <p>
     * The path is computed using the supplied pathfinder, then applied to the mob's walking queue on the game
     * executor. The path is dropped instead of applied if it went stale in the meantime: if a newer path was
     * requested, if paths were {@link #discardPaths() discarded}, if the request it was computed for is no longer
     * active, or if the mob has since moved more than {@link #MAX_PATH_DRIFT} tiles from where the path starts.
     *
     * @param request The navigation request that owns this path.
     * @param destination The destination to walk to.
     * @param pathfinder The pathfinder implementation to use.
     * @param async {@code true} to perform pathfinding asynchronously, otherwise {@code false}.
     * @return A future that completes once the path has been computed and queued, or dropped.
     */
    CompletableFuture<Void> walk(Locatable destination, GamePathfinder<Position> pathfinder, boolean async) {
        Position start = mob.getPosition();
        NavigationRequest request = active;
        int generation = pathGeneration.incrementAndGet();
        CompletableFuture<Void> result = findPath(start, destination.abs(), pathfinder, async)
                .thenAcceptAsync(path -> {
                    if (isStale(start, request, generation)) {
                        return;
                    }
                    mob.getWalking().replacePath(path);
                }, mob.getService().getGameExecutor());
        return handleExceptions(destination, result);
    }

    /**
     * Paths to a usable object interaction tile rather than the object's occupied origin. Reach is checked on the
     * game thread using the same rotated footprint/access/collision rules as the eventual interaction. Try another
     * legal side if the nearest side has no complete route; retain a partial route for long-distance travel.
     */
    CompletableFuture<Void> walkToObject(GameObject target, Optional<Direction> offsetDir,
                                         GamePathfinder<Position> pathfinder, boolean async) {
        Position start = mob.getPosition();
        NavigationRequest request = active;
        int generation = pathGeneration.incrementAndGet();
        List<Position> candidates = objectApproachPositions(target, offsetDir);
        var search = async ? CompletableFuture.supplyAsync(() -> findObjectPath(start, candidates, pathfinder), pool) :
                CompletableFuture.completedFuture(findObjectPath(start, candidates, pathfinder));
        var result = search.thenAcceptAsync(path -> {
            if (!isStale(start, request, generation)) {
                mob.getWalking().replacePath(path);
            }
        }, mob.getService().getGameExecutor());
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) {
                search.cancel(false);
            }
        });
        return handleExceptions(target, result);
    }

    /**
     * Checks for a complete walking route to a legal interaction tile without moving the mob.
     * Partial paths are not proof of reachability. Targets and candidate tiles are captured on the game thread;
     * searches run on the pathfinding pool and return false if no complete route is confirmed within five seconds.
     * Each running search retains the pathfinder's existing node bound; timeout prevents further candidate searches.
     */
    public CompletableFuture<Boolean> canReachForInteraction(Entity target) {
        Position start = mob.getPosition();
        if (start.getZ() != target.getPosition().getZ()) {
            return CompletableFuture.completedFuture(false);
        }
        if (collisionManager.reached(mob, target, InteractionPolicy.STANDARD_SIZE)) {
            return CompletableFuture.completedFuture(true);
        }
        List<Position> candidates = interactionApproachPositions(target, Optional.empty());
        GamePathfinder<Position> pathfinder = getDefaultPathfinder();
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        result.completeAsync(() -> {
            for (Position candidate : candidates) {
                if (result.isDone()) {
                    return false;
                }
                PathResult<Position> path = pathfinder.find(start, candidate);
                if (path.getType() == PathResultType.COMPLETE ||
                        (path.getType() == PathResultType.EMPTY && start.equals(candidate))) {
                    return true;
                }
            }
            return false;
        }, pool);
        CompletableFuture<Boolean> checked = result.completeOnTimeout(false, 5, TimeUnit.SECONDS)
                .exceptionally(error -> {
                    logger.debug("Could not confirm an interaction route from {}.", start, error);
                    return false;
                });
        checked.whenComplete((reachable, error) -> {
            if (checked.isCancelled()) {
                result.cancel(false);
            }
        });
        return checked;
    }

    /** Returns the full legal perimeter, including interior tiles along a multi-tile object's sides. */
    List<Position> objectApproachPositions(GameObject target, Optional<Direction> offsetDir) {
        return interactionApproachPositions(target, offsetDir);
    }

    private List<Position> interactionApproachPositions(Entity target, Optional<Direction> offsetDir) {
        List<Position> candidates = new ArrayList<>();
        if (offsetDir.isPresent()) {
            candidates.add(computeOffsetPosition(target, offsetDir));
        } else {
            Position origin = target.getPosition();
            for (int x = origin.getX() - mob.sizeX() + 1; x < origin.getX() + target.sizeX(); x++) {
                candidates.add(new Position(x, origin.getY() - mob.sizeY(), origin.getZ()));
                candidates.add(new Position(x, origin.getY() + target.sizeY(), origin.getZ()));
            }
            for (int y = origin.getY() - mob.sizeY() + 1; y < origin.getY() + target.sizeY(); y++) {
                candidates.add(new Position(origin.getX() - mob.sizeX(), y, origin.getZ()));
                candidates.add(new Position(origin.getX() + target.sizeX(), y, origin.getZ()));
            }
        }
        return candidates.stream()
                .filter(position -> collisionManager.reached(position, target, InteractionPolicy.STANDARD_SIZE))
                .sorted(java.util.Comparator.comparingInt(position -> position.computeLongestDistance(mob.getPosition())))
                .toList();
    }

    /** Complete endpoints are preferred over a nearer side's fallback path. */
    Deque<Position> findObjectPath(Position start, List<Position> candidates, GamePathfinder<Position> pathfinder) {
        Deque<Position> partial = null;
        int partialDistance = Integer.MAX_VALUE;
        for (Position candidate : candidates) {
            PathResult<Position> result = pathfinder.find(start, candidate);
            Deque<Position> path = result.getPath();
            if (result.getType() == PathResultType.COMPLETE ||
                    (result.getType() == PathResultType.EMPTY && start.equals(candidate))) {
                return path;
            }
            if (result.getType() == PathResultType.PARTIAL && path != null && !path.isEmpty()) {
                int distance = path.peekLast().computeLongestDistance(candidate);
                if (distance < partialDistance) {
                    partial = path;
                    partialDistance = distance;
                }
            }
        }
        return partial;
    }

    /**
     * Determines if a path that was computed from {@code start} should no longer be applied. Must be called on the
     * game thread.
     *
     * @param start The position the path was computed from.
     * @param request The request that was active when the path was requested.
     * @param generation The path generation of the request.
     * @return {@code true} if the path is stale and should be dropped.
     */
    private boolean isStale(Position start, NavigationRequest request, int generation) {
        if (generation != pathGeneration.get()) {
            return true;
        }
        if (request != null && (active != request || request.getPending().isDone())) {
            return true;
        }
        Position current = mob.getPosition();
        return current.getZ() != start.getZ() || current.computeLongestDistance(start) > MAX_PATH_DRIFT;
    }

    /**
     * Selects the default pathfinder for this mob: {@link PathfinderType#BOT} for bots, {@link PathfinderType#PLAYER}
     * for other players and {@link PathfinderType#NPC} for NPCs.
     *
     * @return The default pathfinder for this mob.
     */
    GamePathfinder<Position> getDefaultPathfinder() {
        PathfinderType type;
        if (mob instanceof Bot) {
            type = PathfinderType.BOT;
        } else if (mob instanceof Player) {
            type = PathfinderType.PLAYER;
        } else {
            type = PathfinderType.NPC;
        }
        return type.getPfFunction().apply(mob);
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
        CompletableFuture<T> handled = result.exceptionally(ex -> {
            Throwable cause = ex;
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            boolean ignored = cause instanceof CancellationException;
            if (!ignored) {
                logger.error("Pathfinding for mob {} to target {} failed!", mob, target, ex);
            }
            return null;
        });
        handled.whenComplete((value, error) -> {
            if (handled.isCancelled()) {
                result.cancel(false);
            }
        });
        return handled;
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
        if (offsetDir.isEmpty()) {
            // Default interaction approaches must end at a side that satisfies the same reach check as combat.
            var candidates = Direction.NESW.stream()
                    .map(direction -> computeOffsetPosition(target, Optional.of(direction))).toList();
            java.util.Comparator<Position> distance = java.util.Comparator.comparingInt(
                    candidate -> candidate.computeLongestDistance(mob.getPosition()));
            return candidates.stream()
                    .filter(candidate -> collisionManager.reached(candidate, target, InteractionPolicy.STANDARD_SIZE))
                    .min(distance).orElseGet(() -> candidates.stream().min(distance).orElseThrow());
        }
        Position targetPosition = target.getPosition();
        int sizeX = mob.sizeX();
        int sizeY = mob.sizeY();

        int targetSizeX = target.sizeX();
        int targetSizeY = target.sizeY();

        Position position = mob.getPosition();
        int height = position.getZ();

        Direction direction = offsetDir.get();
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
