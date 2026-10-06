package io.luna.game.model.path.route;

import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.model.path.FallbackPathfinder;
import io.luna.game.model.path.GamePathfinder;
import io.luna.game.model.path.PathResult;
import io.luna.game.model.path.PathResultType;
import io.luna.game.model.path.Pathfinders;

import java.util.ArrayDeque;
import java.util.concurrent.ThreadLocalRandom;

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
     * The chance, from {@code 0.0} to {@code 1.0}, that a search takes the route of the game, which is the
     * {@link RouteStyle#STANDARD} one. The rest of the time the route is of a different shape.
     */
    private final double standardChance;

    /**
     * Creates a new {@link RoutePathfinder} that always takes the routes of the game.
     *
     * @param collisionManager The collision manager.
     * @param size The width and length of the mob, in tiles.
     * @param extraFlag The flags that also stop a step, such as {@link CollisionFlag#BLOCK_NPCS}.
     * @param strategy The rule for moving onto a tile.
     */
    public RoutePathfinder(CollisionManager collisionManager, int size, int extraFlag, RouteStrategy strategy) {
        this(collisionManager, size, extraFlag, strategy, 1.0);
    }

    /**
     * Creates a new {@link RoutePathfinder}.
     *
     * @param collisionManager The collision manager.
     * @param size The width and length of the mob, in tiles.
     * @param extraFlag The flags that also stop a step, such as {@link CollisionFlag#BLOCK_NPCS}.
     * @param strategy The rule for moving onto a tile.
     * @param standardChance The chance, from {@code 0.0} to {@code 1.0}, that a search takes the route of the game
     * instead of one of a different shape.
     */
    public RoutePathfinder(CollisionManager collisionManager, int size, int extraFlag, RouteStrategy strategy,
                           double standardChance) {
        super(collisionManager);
        this.size = size;
        this.extraFlag = extraFlag;
        this.strategy = strategy;
        this.standardChance = standardChance;
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
                target.getX(), target.getY(), size, extraFlag, strategy, true, MAX_WAYPOINTS, chooseStyle());
        if (!route.isSuccess() || route.getWaypoints().isEmpty()) {
            // Either nothing was found, or the closest tile that could be reached is the one the mob is on.
            return failed();
        }

        PathResultType type = route.isAlternative() ? PathResultType.PARTIAL : PathResultType.COMPLETE;
        return new PathResult<>(type, new ArrayDeque<>(route.getWaypoints()));
    }

    /**
     * Picks the shape of the route for a search: the route of the game with the chance of {@link #standardChance}, and
     * otherwise either a route that turns early or a random one.
     */
    private RouteStyle chooseStyle() {
        if (standardChance >= 1.0) {
            return RouteStyle.STANDARD;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() < standardChance) {
            return RouteStyle.STANDARD;
        }
        return random.nextBoolean() ? RouteStyle.DIAGONAL_FIRST : RouteStyle.RANDOM;
    }

    /**
     * Creates the result of a search that found nothing.
     */
    private static PathResult<Position> failed() {
        return new PathResult<>(PathResultType.FAILED, new ArrayDeque<>(0));
    }
}
