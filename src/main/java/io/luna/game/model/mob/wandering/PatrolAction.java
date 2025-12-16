package io.luna.game.model.mob.wandering;

import com.google.common.collect.ImmutableList;
import io.luna.LunaContext;
import io.luna.game.GameService;
import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.WalkingQueue;
import io.luna.util.RandomUtils;
import io.luna.util.Rational;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * An {@link Action} that causes a {@link Mob} to patrol between a fixed set of waypoints.
 * <p>
 * The patrol behaviour is controlled by:
 * <ul>
 *     <li>A list of {@link Position} waypoints that define the patrol route.</li>
 *     <li>A {@link WanderingFrequency} that determines how often the mob attempts to move.</li>
 *     <li>An optional {@code scrambleRoute} flag that makes the mob pick random waypoints instead of
 *     traversing them sequentially.</li>
 * </ul>
 *
 * @author lare96
 */
public final class PatrolAction extends Action<Mob> {

    /**
     * Builder for {@link PatrolAction} instances.
     * <p>
     * Use this to configure:
     * <ul>
     *     <li>The patrolling mob.</li>
     *     <li>The list of waypoint positions.</li>
     *     <li>Whether the route is scrambled (random waypoint order) or sequential.</li>
     *     <li>The {@link WanderingFrequency} used to decide how often to attempt movement.</li>
     * </ul>
     * <p>
     * The builder enforces that at least two waypoints are present before construction.
     */
    public static final class Builder {

        /**
         * Mob that will execute the patrol.
         */
        private final Mob mob;

        /**
         * Mutable builder backing the final immutable waypoint list.
         */
        private final ImmutableList.Builder<Position> waypoints = ImmutableList.builder();

        /**
         * If {@code true}, waypoints are visited in random order instead of sequentially.
         */
        private boolean scrambleRoute;

        /**
         * Controls how often this action attempts to move to the next waypoint.
         */
        private WanderingFrequency frequency = WanderingFrequency.NORMAL;

        /**
         * Creates a new builder for a {@link PatrolAction} targeting the given mob.
         *
         * @param mob The mob that will patrol along the configured waypoints.
         */
        public Builder(Mob mob) {
            this.mob = mob;
        }

        /**
         * Adds a single waypoint to the patrol route.
         *
         * @param position The waypoint position in world coordinates.
         * @return This builder, for chaining.
         */
        public Builder add(Position position) {
            waypoints.add(position);
            return this;
        }

        /**
         * Adds all positions in the given collection as waypoints to the patrol route.
         *
         * @param positions A collection of waypoint positions to add.
         * @return This builder, for chaining.
         */
        public Builder addAll(Collection<Position> positions) {
            waypoints.addAll(positions);
            return this;
        }

        /**
         * Enables scrambled routing, causing the patrol to pick random waypoints instead of
         * following the list sequentially.
         * <p>
         * When scrambled, the same waypoint will not be selected twice in a row.
         *
         * @return This builder, for chaining.
         */
        public Builder scrambleRoute() {
            scrambleRoute = true;
            return this;
        }

        /**
         * Sets the {@link WanderingFrequency} that controls how often the mob attempts to move
         * to its next waypoint.
         *
         * @param frequency The desired wandering frequency.
         * @return This builder, for chaining.
         * @throws NullPointerException If {@code frequency} is {@code null}.
         */
        public Builder setFrequency(WanderingFrequency frequency) {
            this.frequency = Objects.requireNonNull(frequency);
            return this;
        }

        /**
         * Builds a new {@link PatrolAction} instance with the configured parameters.
         *
         * @return A new {@link PatrolAction}.
         * @throws IllegalStateException If fewer than two waypoints have been configured.
         */
        public PatrolAction build() {
            ImmutableList<Position> immutableWaypoints = waypoints.build();
            if (immutableWaypoints.size() < 2) {
                throw new IllegalStateException("Must have at least 2 valid waypoints in a PatrolAction.");
            }
            return new PatrolAction(mob, immutableWaypoints, frequency, scrambleRoute);
        }
    }

    /**
     * Immutable list of patrol waypoints.
     */
    private final ImmutableList<Position> waypoints;

    /**
     * How often to attempt a patrol step.
     */
    private final WanderingFrequency frequency;

    /**
     * If {@code true}, waypoints are picked in random order instead of sequentially.
     */
    private final boolean scrambleRoute;

    /**
     * Index of the next waypoint to visit when not scrambling the route.
     */
    private int nextWaypointIndex;

    /**
     * Index of the last waypoint we pathed to, used to avoid immediate repetition when scrambling.
     */
    private int lastWaypointIndex;

    /**
     * Handle to the asynchronous pathfinding job for the current/next waypoint.
     * <p>
     * This is initialised as a completed future so that the first execution can schedule a new path immediately.
     */
    private CompletableFuture<Void> pathJob = CompletableFuture.completedFuture(null);

    /**
     * Creates a new {@link PatrolAction}.
     * <p>
     * The constructor is private; use {@link Builder} to construct instances.
     *
     * @param mob The mob that will patrol.
     * @param waypoints Immutable list of waypoints to patrol between.
     * @param frequency How often to attempt a patrol step.
     * @param scrambleRoute If {@code true}, waypoints are selected randomly rather than in sequence.
     */
    private PatrolAction(Mob mob,
                         ImmutableList<Position> waypoints,
                         WanderingFrequency frequency,
                         boolean scrambleRoute) {
        super(mob, ActionType.WEAK, true, 3);
        this.waypoints = waypoints;
        this.frequency = frequency;
        this.scrambleRoute = scrambleRoute;
    }

    @Override
    public boolean run() {
        LunaContext context = world.getContext();
        GameService game = context.getGame();

        // Already moving or still generating path to waypoints.
        if (mob.isLocked() || !mob.getWalking().isEmpty() || !pathJob.isDone()) {
            return false;
        }

        Rational chance = frequency.getChance();
        if (RandomUtils.roll(chance)) {
            int targetIndex = scrambleRoute ? RandomUtils.exclusive(0, waypoints.size()) : nextWaypointIndex;
            if (scrambleRoute && targetIndex == lastWaypointIndex) {
                // If we've already pathed to this waypoint last, re-roll on next execution.
                return false;
            }
            pathJob = computeAndQueuePathAsync(game, waypoints.get(targetIndex));
            lastWaypointIndex = targetIndex;

            // Check if end of route has been reached, if so reset.
            if (!scrambleRoute) {
                if (++nextWaypointIndex >= waypoints.size()) {
                    nextWaypointIndex = 0;
                }
            }
        }
        return false;
    }

    /**
     * Submits an asynchronous pathfinding task to the {@link GameService} to compute a path to the given destination
     * and, once computed, queues the resulting steps onto the mob's {@link WalkingQueue}.
     * <p>
     * Pathfinding is performed off the main thread, and {@link WalkingQueue#addPath} is executed on the game executor
     * to ensure all walking mutations happen on the correct thread.
     *
     * @param game The game service used to submit the pathfinding job.
     * @param dest The destination position to path towards.
     * @return A {@link CompletableFuture} that completes once the path has been queued, or completes exceptionally
     * if pathfinding fails.
     */
    private CompletableFuture<Void> computeAndQueuePathAsync(GameService game, Position dest) {
        WalkingQueue walking = mob.getWalking();
        return game.submit(() -> walking.findPath(dest, true)).
                thenAcceptAsync(walking::addPath, game.getGameExecutor())
                .exceptionally(ex -> {
                    logger.catching(ex);
                    return null;
                });
    }
}
