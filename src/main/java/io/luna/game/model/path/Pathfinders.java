package io.luna.game.model.path;

import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionFlag;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.path.astar.PlayerPathfinder;
import io.luna.game.model.path.route.RoutePathfinder;
import io.luna.game.model.path.route.RouteStrategy;

/**
 * Creates the pathfinders that suit each kind of mob.
 * <p>
 * Routes are found with a {@link RoutePathfinder}, which only searches around the mob. Destinations farther away are
 * given to a {@link PlayerPathfinder}, which has no such limit, by a {@link FallbackPathfinder}.
 *
 * @author hydrozoa
 */
public final class Pathfinders {

    /**
     * Creates a pathfinder for a player. Players are never stopped by NPCs or by other players, so the routes lead
     * straight through them.
     *
     * @param mob The player that will walk the routes.
     * @return The new pathfinder.
     */
    public static GamePathfinder<Position> forPlayer(Mob mob) {
        return withFallback(new RoutePathfinder(mob.getWorld().getCollisionManager(), mob.size(), 0,
                RouteStrategy.NORMAL, 1.0));
    }

    /**
     * Creates a pathfinder for an NPC. NPCs are stopped by other NPCs and by players, so the routes go around them.
     * The NPC moves onto tiles as its {@link Mob#getRouteStrategy() route strategy} allows.
     * <p>
     * Only an NPC with the {@link RouteStrategy#NORMAL} strategy falls back to a {@link PlayerPathfinder}. That one
     * knows nothing of strategies, and would lead any other NPC off the terrain it belongs to, so those NPCs fail to
     * find destinations that are too far away.
     *
     * @param mob The NPC that will walk the routes.
     * @return The new pathfinder.
     */
    public static GamePathfinder<Position> forNpc(Mob mob) {
        RoutePathfinder routes = new RoutePathfinder(mob.getWorld().getCollisionManager(), mob.size(),
                CollisionFlag.BLOCK_NPCS | CollisionFlag.BLOCK_PLAYERS, mob.getRouteStrategy(), 1.0);
        return mob.getRouteStrategy() == RouteStrategy.NORMAL ? withFallback(routes) : routes;
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
    public static GamePathfinder<Position> forBot(Mob mob) {
        double standardChance = 1.0;
        if (mob instanceof Bot) {
            standardChance = ((Bot) mob).getPersonality().getIntelligence();
        }
        return withFallback(new RoutePathfinder(mob.getWorld().getCollisionManager(), mob.size(), 0,
                RouteStrategy.NORMAL, standardChance));
    }

    /**
     * Wraps a route pathfinder so that destinations beyond its reach are given to a {@link PlayerPathfinder}.
     */
    private static GamePathfinder<Position> withFallback(RoutePathfinder routes) {
        return new FallbackPathfinder<>(routes,
                origin -> new PlayerPathfinder(routes.collisionManager, origin.getZ()));
    }

    private Pathfinders() {}
}
