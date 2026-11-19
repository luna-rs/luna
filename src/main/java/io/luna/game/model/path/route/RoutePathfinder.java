package io.luna.game.model.path.route;

import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.model.collision.CollisionView;
import io.luna.game.model.path.FallbackPathfinder;
import io.luna.game.model.path.GamePathfinder;
import io.luna.game.model.path.PathResult;
import io.luna.game.model.path.PathResultType;
import io.luna.game.model.path.Pathfinders;

import java.util.ArrayDeque;

/**
 * A pathfinder for mobs of any size that finds routes with a {@link RouteFinder}.
 * <p>
 * Routes are found in the collision snapshots, so searches are safe to run on any thread. A route is a list of
 * waypoints, which the walking queue fills in with the tiles between them.
 * <p>
 * The search only covers about {@value RouteFinder#SEARCH_SIZE} tiles around the mob. Destinations farther away are
 * not {@link #supports(Position, Position) supported}, and fail when searched for. A {@link FallbackPathfinder} can
 * give them to another pathfinder.
 * <p>
 * Use {@link Pathfinders} to create the pathfinder that suits a mob.
 *
 * @author hydrozoa
 */
public final class RoutePathfinder extends GamePathfinder<Position> {

    /**
     * The most waypoints a route is given. A route has at most one waypoint per turn.
     */
    private static final int MAX_WAYPOINTS = 255;

    /**
     * The farthest, along either axis, that a destination can be for the {@link RouteFinder} to be used. This is
     * short of the edge of the search area so that there is room to go around obstacles.
     */
    private static final int MAX_RANGE = RouteFinder.SEARCH_SIZE / 2 - 8;

    /**
     * The route finders of the threads that search. A route finder reuses its buffers, so it can't be shared.
     */
    private static final ThreadLocal<RouteFinder> FINDERS = ThreadLocal.withInitial(RouteFinder::new);

    /**
     * The width and length of the mob, in tiles.
     */
    private final int size;

    /**
     * The flags that also stop a step, on top of walls, objects and blocked terrain.
     */
    private final int extraFlag;

    /**
     * The rule for moving onto a tile.
     */
    private final RouteStrategy strategy;

    /**
     * Creates a new {@link RoutePathfinder} that always takes the routes of the game.
     *
     * @param collisionManager The collision manager.
     * @param size The width and length of the mob, in tiles.
     * @param extraFlag The flags that also stop a step, such as {@link CollisionFlag#BLOCK_NPCS}.
     * @param strategy The rule for moving onto a tile.
     */
    public RoutePathfinder(CollisionManager collisionManager, int size, int extraFlag, RouteStrategy strategy) {
        super(collisionManager);
        this.size = size;
        this.extraFlag = extraFlag;
        this.strategy = strategy;
    }

    @Override
    public boolean supports(Position origin, Position target) {
        // Searches between planes are left to fail in find, as no pathfinder could do them.
        return origin.getZ() != target.getZ() ||
                (Math.abs(origin.getX() - target.getX()) <= MAX_RANGE &&
                        Math.abs(origin.getY() - target.getY()) <= MAX_RANGE);
    }

    @Override
    public PathResult<Position> find(Position origin, Position target) {
        if (origin.getZ() != target.getZ()) {
            return failed();
        } else if (origin.equals(target)) {
            return new PathResult<>(PathResultType.EMPTY, new ArrayDeque<>(0));
        } else if (!supports(origin, target)) {
            return failed();
        }

        Route route = FINDERS.get().find(collisionManager.view(true), origin.getZ(), origin.getX(), origin.getY(),
                target.getX(), target.getY(), size, extraFlag, strategy, true, MAX_WAYPOINTS);
        return toPathResult(route);
    }

    /**
     * Routes to the closest walkable tile with a clear attack from the supplied range. Movement and sight use
     * the same collision view, and a failed search retains the usual closest-tile fallback.
     */
    public PathResult<Position> findInLineOfSight(Position origin, Position target, int targetSize, int range) {
        if (!origin.isViewable(target)) {
            return failed();
        }
        CollisionView view = collisionManager.view(true);
        int x = target.getX();
        int y = target.getY();
        int level = origin.getZ();
        // Match live reach: visibility uses the anchors and range uses the nearest footprint edges.
        int before = Math.min(Position.VIEWING_DISTANCE, range + size - 1);
        int after = Math.min(Position.VIEWING_DISTANCE, range + targetSize - 1);
        Route route = FINDERS.get().find(view, level, origin.getX(), origin.getY(), x, y, size, extraFlag, strategy,
                true, MAX_WAYPOINTS, (srcX, srcY) -> srcX >= x - before && srcX <= x + after &&
                        srcY >= y - before && srcY <= y + after && (srcX != x || srcY != y) &&
                        LineOfSight.hasLineOfSight(view, level, srcX, srcY, x, y, size, size, targetSize, targetSize, 0));
        return toPathResult(route);
    }

    private static PathResult<Position> toPathResult(Route route) {
        if (!route.isSuccess() || (route.isAlternative() && route.getWaypoints().isEmpty())) {
            // Either nothing was found, or the closest tile that could be reached is the one the mob is on.
            return failed();
        }
        if (route.getWaypoints().isEmpty()) {
            return new PathResult<>(PathResultType.EMPTY, new ArrayDeque<>(0));
        }

        PathResultType type = route.isAlternative() ? PathResultType.PARTIAL : PathResultType.COMPLETE;
        return new PathResult<>(type, new ArrayDeque<>(route.getWaypoints()));
    }

    /**
     * Creates the result of a search that found nothing.
     */
    private static PathResult<Position> failed() {
        return new PathResult<>(PathResultType.FAILED, new ArrayDeque<>(0));
    }
}
