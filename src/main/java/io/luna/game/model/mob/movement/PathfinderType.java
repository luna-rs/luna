package io.luna.game.model.mob.movement;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.path.BotPathfinder;
import io.luna.game.model.path.GamePathfinder;
import io.luna.game.model.path.PlayerPathfinder;
import io.luna.game.model.path.SimplePathfinder;

import java.util.function.Function;

/**
 * Represents the pathfinder strategy used by a {@link Mob}.
 * <p>
 * Each type creates the appropriate {@link GamePathfinder} implementation for the supplied mob. This allows
 * players, bots, and simple entities to use different pathfinding behavior while sharing the same movement system.
 *
 * @author lare96
 */
public enum PathfinderType {

    /**
     * A full player pathfinder that uses collision data and the mob's current height level.
     */
    PLAYER(mob -> new PlayerPathfinder(mob.getWorld().getCollisionManager(), mob.getZ())),

    /**
     * A bot-specific pathfinder that uses collision data and the mob's current height level.
     */
    BOT(mob -> new BotPathfinder(mob.getWorld().getCollisionManager(), mob.getZ())),

    /**
     * A simple pathfinder that uses collision data without height-level specific setup.
     */
    DUMB(mob -> new SimplePathfinder(mob.getWorld().getCollisionManager()));

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