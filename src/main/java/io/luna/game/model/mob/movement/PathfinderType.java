package io.luna.game.model.mob.movement;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.path.GamePathfinder;
import io.luna.game.model.path.Pathfinders;

import java.util.function.Function;

/**
 * Represents the pathfinder strategy used by a {@link Mob}.
 * <p>
 * Each type creates a pathfinder, from {@link Pathfinders}, for the supplied mob, which is sized for it. Players and
 * NPCs have a type each, because they differ in how they treat other mobs. Bots use the player type.
 *
 * @author lare96
 */
public enum PathfinderType {

    /**
     * The routes of the game, for players and bots. Players walk through NPCs and through other players, so their
     * routes do too.
     */
    PLAYER(Pathfinders::forPlayer),

    /**
     * The routes of the game, for NPCs. NPCs are stopped by other NPCs and by players, so their routes go around them.
     */
    NPC(Pathfinders::forNpc);

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