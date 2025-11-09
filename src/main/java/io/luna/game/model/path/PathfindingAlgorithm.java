package io.luna.game.model.path;

import com.google.common.base.Preconditions;
import io.luna.game.model.Direction;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;

import java.util.Deque;
import java.util.Optional;

/**
 * An algorithm used to find a path between two {@link Position}s.
 *
 * @author Major
 * @author lare96
 */
public abstract class PathfindingAlgorithm {

    protected final CollisionManager collisionManager;

    /**
     * Creates the PathfindingAlgorithm.
     *
     * @param collisionManager The {@link CollisionManager} used to check if there is a collision
     * between two {@link Position}s in a path.
     */
    public PathfindingAlgorithm(CollisionManager collisionManager) {
        this.collisionManager = collisionManager;
    }

    /**
     * Finds a valid path from the origin {@link Position} to the target one.
     *
     * @param origin The origin Position.
     * @param target The target Position.
     * @return The {@link Deque} containing the Positions to go through.
     */
    public abstract Deque<Position> find(Position origin, Position target);

    /**
     * Returns whether or not a {@link Position} walking one step in any of the specified {@link Direction}s would lead
     * to is traversable.
     *
     * @param current The current Position.
e     * @param directions The Directions that should be checked.
     * @return {@code true} if any of the Directions lead to a traversable tile, otherwise {@code false}.
     */
    protected boolean traversable(Position current, Direction... directions) {
        Preconditions.checkArgument(directions != null && directions.length > 0, "Directions array cannot be null.");
        for (Direction direction : directions) {
            if (collisionManager.traversable(current, EntityType.NPC, direction, true)) {
                return true;
            }
        }
        return false;
    }
}