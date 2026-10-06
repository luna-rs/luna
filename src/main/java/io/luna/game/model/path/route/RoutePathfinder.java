package io.luna.game.model.path.route;

import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionFlag;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.path.GamePathfinder;
import io.luna.game.model.path.PathResult;
import io.luna.game.model.path.PathResultType;
import io.luna.game.model.path.astar.PlayerPathfinder;

import java.util.ArrayDeque;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A pathfinder for mobs of any size that finds routes with a {@link RouteFinder}.
 * <p>
 * Routes are found in the collision snapshots, so searches are safe to run on any thread. A route is a list of
 * waypoints, which the walking queue fills in with the tiles between them.
 * <p>
 * The search only covers about {@value RouteFinder#SEARCH_SIZE} tiles around the mob. Destinations farther away are
 * given to a {@link PlayerPathfinder}, which has no such limit. That one knows nothing of a {@link RouteStrategy}, so
 * when the strategy is not {@link RouteStrategy#NORMAL} such destinations are not searched for and the search fails.
 * <p>
 * Use {@link #forPlayer(Mob)}, {@link #forNpc(Mob)} and {@link #forBot(Mob)} to create the pathfinder that suits a mob.
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
     * Creates a pathfinder for a player. Players are never stopped by NPCs or by other players, so the routes lead
     * straight through them.
     *
     * @param mob The player that will walk the routes.
     * @return The new pathfinder.
     */
    public static RoutePathfinder forPlayer(Mob mob) {
        return new RoutePathfinder(mob.getWorld().getCollisionManager(), mob.size(), 0, RouteStrategy.NORMAL, 1.0);
    }

    /**
     * Creates a pathfinder for an NPC. NPCs are stopped by other NPCs and by players, so the routes go around them.
     * The NPC moves onto tiles as its {@link Mob#getRouteStrategy() route strategy} allows.
     *
     * @param mob The NPC that will walk the routes.
     * @return The new pathfinder.
     */
    public static RoutePathfinder forNpc(Mob mob) {
        return new RoutePathfinder(mob.getWorld().getCollisionManager(), mob.size(),
                CollisionFlag.BLOCK_NPCS | CollisionFlag.BLOCK_PLAYERS, mob.getRouteStrategy(), 1.0);
    }

    /**
     * Creates a pathfinder for a bot, which does not always take the same route that a player would.
     * <p>
     * There is usually more than one route of the shortest length between two tiles. A player's pathfinder always
     * takes the same one, and a bot would too, so that bots going the same way would walk in identical lines. Instead
     * a bot takes the player's route with a chance equal to its intelligence, and otherwise takes a route of a
     * different shape. Like players, bots are never stopped by other mobs. A mob that is not a bot always takes the
     * player's route.
     *
     * @param mob The bot that will walk the routes.
     * @return The new pathfinder.
     */
    public static RoutePathfinder forBot(Mob mob) {
        double standardChance = 1.0;
        if (mob instanceof Bot) {
            standardChance = ((Bot) mob).getPersonality().getIntelligence();
        }
        return new RoutePathfinder(mob.getWorld().getCollisionManager(), mob.size(), 0, RouteStrategy.NORMAL,
                standardChance);
    }

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
    public PathResult<Position> find(Position origin, Position target) {
        if (origin.getZ() != target.getZ()) {
            return failed();
        } else if (origin.equals(target)) {
            return new PathResult<>(PathResultType.EMPTY, new ArrayDeque<>(0));
        } else if (Math.abs(origin.getX() - target.getX()) > MAX_RANGE ||
                Math.abs(origin.getY() - target.getY()) > MAX_RANGE) {
            if (strategy != RouteStrategy.NORMAL) {
                // The fallback knows nothing of strategies, and would lead the mob off the terrain it belongs to.
                return failed();
            }
            return new PlayerPathfinder(collisionManager, origin.getZ()).find(origin, target);
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
