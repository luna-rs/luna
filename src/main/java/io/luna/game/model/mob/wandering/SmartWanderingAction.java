package io.luna.game.model.mob.wandering;

import io.luna.LunaContext;
import io.luna.game.GameService;
import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.cache.map.MapIndex;
import io.luna.game.cache.map.MapIndexTable;
import io.luna.game.cache.map.MapTile;
import io.luna.game.cache.map.MapTileGrid;
import io.luna.game.model.Position;
import io.luna.game.model.area.Area;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.path.AStarPathfinder;
import io.luna.game.model.path.PlayerPathfinder;
import io.luna.util.RandomUtils;
import io.luna.util.Rational;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A "smart" wandering {@link Action} that moves a {@link Mob} around an {@link Area} by:
 * <ul>
 *     <li>Pre-sampling random destinations inside the wander area.</li>
 *     <li>Filtering out obviously invalid tiles (water/blocked tiles from cache + collision blocked tiles).</li>
 *     <li>Asynchronously pathfinding to the chosen destination.</li>
 *     <li>Queueing the resulting path in small randomized slices to spread movement over time.</li>
 * </ul>
 * <p>
 * This action is intended for larger wander ranges where a single-tile random-walk can look unnatural.
 * It favors "pathing somewhere" behavior while keeping work distributed (destination sampling + async path build +
 * incremental queueing).
 * </p>
 *
 * @author lare96
 */
public final class SmartWanderingAction extends Action<Mob> {

    /**
     * The allowed wander area. All sampled destinations must be inside this area.
     */
    private final Area area;

    /**
     * Controls how often a new slice of waypoints is queued (i.e., how "active" the wander looks).
     */
    private final WanderingFrequency frequency;

    /**
     * Pre-sampled destination candidates inside {@link #area}. When empty, it is repopulated.
     */
    private final Queue<Position> destinations = new ArrayDeque<>();

    /**
     * The current computed path broken into absolute {@link Position} waypoints. These are consumed in chunks by
     * {@link #queueNextWaypointSlice()}.
     */
    private final Queue<Position> waypoints = new ArrayDeque<>();

    /**
     * Tracks the in-flight async waypoint computation. This is used to prevent queuing multiple pathfinding requests
     * at once.
     */
    private CompletableFuture<Void> waypointFuture = CompletableFuture.completedFuture(null);

    /**
     * Creates a new {@link SmartWanderingAction}.
     *
     * @param mob The mob that will wander.
     * @param area The wander area (destination sampling bounds).
     * @param frequency How often to enqueue movement slices when idle.
     */
    public SmartWanderingAction(Mob mob, Area area, WanderingFrequency frequency) {
        super(mob, ActionType.WEAK, true, 3);
        this.area = area;
        this.frequency = frequency;
    }

    @Override
    public boolean run() {
        LunaContext context = world.getContext();
        GameService game = context.getGame();

        // Already moving or still generating waypoints.
        if (mob.isLocked() || !mob.getWalking().isEmpty() || !waypointFuture.isDone()) {
            return false;
        }

        // No waypoints, generate new ones.
        if (waypoints.isEmpty()) {
            Position dest = destinations.poll();

            // No more pre-computed destinations, generate them.
            if (dest == null) {
                populateDestinationQueue();
                return false;
            }

            // Asynchronously generate path to destination and add waypoints.
            waypointFuture = buildWaypointsAsync(game, dest);
            return false;
        }

        Rational chance = frequency.getChance();
        if (RandomUtils.roll(chance)) {
            // We have waypoints, walk through them in batches until the destination is reached.
            queueNextWaypointSlice();
            return false;
        }
        return false;
    }

    /**
     * Populates {@link #destinations} by sampling random positions within {@link #area} and filtering out tiles that
     * are:
     * <ul>
     *     <li>Missing map index/tile data (safety guard).</li>
     *     <li>Water or blocked according to {@link MapTile} flags.</li>
     *     <li>Blocked according to the live collision matrix.</li>
     * </ul>
     *
     * <p>
     * This is a cheap pre-filter so that the more expensive pathfinder is less likely to be called on impossible or
     * undesirable destinations.
     * </p>
     */
    private void populateDestinationQueue() {
        // TODO If nothing found in common areas, sample from zones.
        MapIndexTable indexTable = world.getContext().getCache().getMapIndexTable();
        for (int loop = 0; loop < 50; loop++) {
            Position nextPosition = area.randomPosition();

            // Resolve the region's map index, then tile grid, then tile at the chosen position.
            MapIndex index = indexTable.getIndexTable().get(nextPosition.getRegion());
            if (index == null) {
                continue;
            }
            MapTileGrid tileGrid = indexTable.getTileSet().getGrid(index);
            if (tileGrid == null) {
                continue;
            }

            MapTile tile = tileGrid.getTile(nextPosition);
            if (tile == null || tile.isWater() || tile.isBlocked()) {
                continue;
            }

            // Live collision filter (helps avoid picking tiles inside dynamic/static blockage).
            if (!world.getCollisionManager().isBlocked(nextPosition, false)) {
                destinations.add(nextPosition);
            }
        }
    }

    /**
     * Asynchronously computes a full path from the mob's current position to {@code dest}, then converts the path into
     * absolute {@link Position} waypoints and stores them in {@link #waypoints}.
     * <p>
     * The heavy path computation is performed via {@link GameService#submit(Supplier)} and the result is applied using
     * {@code thenAcceptAsync(..., game.getGameExecutor())} so waypoint mutation remains serialized on the game thread.
     * </p>
     *
     * @param game The game service used to schedule the path computation.
     * @param dest The chosen destination inside {@link #area}.
     * @return A future representing the waypoint build operation.
     */
    private CompletableFuture<Void> buildWaypointsAsync(GameService game, Position dest) {
        AStarPathfinder<Position> pf = new PlayerPathfinder(world.getCollisionManager(), mob.getPosition().getZ());
        return mob.getNavigator().findPath(mob.getPosition(), dest, pf, true).thenAcceptAsync(path -> {
            if (path != null && !path.isEmpty()) {
                // Convert steps into absolute positions. Z is pinned to the mob's current plane.
                List<Position> stepList = path.stream().map(step ->
                        new Position(step.getX(), step.getY(), mob.getZ())).collect(Collectors.toList());
                waypoints.addAll(stepList);
            }
        }, game.getGameExecutor())
                .exceptionally(ex -> {
                    logger.catching(ex);
                    return null;
                });
    }

    /**
     * Dequeues a randomized number of pending {@link #waypoints} and converts them into a {@link Deque} of walking steps,
     * then submits that slice to the mob's walking queue.
     * <p>
     * The slice size is randomized to avoid robotic "perfect chunks" while still bounding the work per tick.
     * </p>
     */
    private void queueNextWaypointSlice() {
        int minSteps = RandomUtils.nextBoolean() ? 1 : Position.VIEWING_DISTANCE / 2;
        int stepsLeft = RandomUtils.inclusive(minSteps, Position.VIEWING_DISTANCE * 2);
        stepsLeft = Math.min(stepsLeft, waypoints.size());

        Deque<Position> path = new ArrayDeque<>(stepsLeft);
        for (; ;) {
            if (stepsLeft < 1) {
                break;
            }
            Position position = waypoints.poll();
            if (position == null) {
                break;
            }
            stepsLeft--;
            path.add(position);
        }
        mob.getWalking().addPath(path);
    }
}
