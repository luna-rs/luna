package io.luna.game.model.mob.movement;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.path.GamePathfinder;
import io.luna.game.model.path.RoutePathfinder;

import java.util.function.Function;

/**
 * Represents the pathfinder strategy used by a {@link Mob}.
 * <p>
 * Each type creates a {@link RoutePathfinder} for the supplied mob, which is sized for it. There is a type for each kind
 * of mob that walks, because they differ in how they treat other mobs and in how much their routes vary.
 *
 * @author lare96
 */
public enum PathfinderType {

    /**
     * The routes of the game, for players. Players walk through NPCs and through other players, so their routes do too.
     */
    PLAYER(RoutePathfinder::forPlayer),

    /**
     * Routes that vary from bot to bot, for bots. A bot takes the route of the game with a chance equal to its
     * intelligence, and otherwise takes a route of a different shape, so that bots do not all walk in identical lines.
     * Like players, bots walk through other mobs.
     */
    BOT(RoutePathfinder::forBot),

    /**
     * The routes of the game, for NPCs. NPCs are stopped by other NPCs and by players, so their routes go around them.
     */
    NPC(RoutePathfinder::forNpc);

    /**
     * Creates a pathfinder instance for a mob.
     */
    private final Function<Mob, GamePathfinder<Position>> pfFunction;

    /**
     * Creates a new pathfinder type.
     *
     * @param pfFunction The function used to create a pathfinder for a mob.
     */
    PathfinderType(Function<Mob, GamePathfinder<Position>> pfFunction) {
        this.pfFunction = pfFunction;
    }

    /**
     * @return The pathfinder creation function.
     */
    public Function<Mob, GamePathfinder<Position>> getPfFunction() {
        return pfFunction;
    }
}