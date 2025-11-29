package io.luna.game.model.path;

import io.luna.game.model.Direction;
import io.luna.game.model.EntityType;
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
import io.luna.game.model.Region;
import io.luna.game.model.collision.CollisionManager;

import java.util.Deque;

/**
 * A generic pathfinding engine capable of computing a path between two {@link Locatable}s.
 * <p>
 * This class defines the common high-level structure for all pathfinding algorithms in Luna, including A*,
 * random-biased pathfinding for bots, and any future specialized strategies. Concrete subclasses are responsible for
 * implementing the specific search logic.
 * </p>
 *
 * @param <T> The locatable type used by this pathfinder (e.g., {@link Position} or {@link Region}).
 * @author lare96
 */
public abstract class GamePathfinder<T extends Locatable> {

    /**
     * The collision manager used for evaluating movement and adjacency rules.
     */
    protected final CollisionManager collisionManager;

    /**
     * Creates a new pathfinder instance.
     *
     * @param collisionManager The {@link CollisionManager} used for tile traversal checks,
     * obstacle detection, and movement validation.
     */
    public GamePathfinder(CollisionManager collisionManager) {
        this.collisionManager = collisionManager;
    }

    /**
     * Computes a valid path from the origin to the target locatable.
     * <p>
     * The returned {@link Deque} contains a sequence of intermediate steps. Implementations may return an
     * empty deque if no path exists.
     * </p>
     *
     * @param origin The starting locatable.
     * @param target The target locatable.
     * @return A deque representing the path to the target, or an empty deque if no route exists.
     */
    public abstract Deque<T> find(T origin, T target);

    /**
     * Determines whether walking one step from {@code current} in the given {@link Direction} is traversable
     * for a player.
     * <p>
     * This is a convenience wrapper around the collision manager's traversability logic. It assumes the moving
     * entity is a {@link EntityType#PLAYER} and performs a single-tile movement check.
     * </p>
     *
     * @param current The current position.
     * @param direction The direction to test.
     * @return {@code true} if the movement is allowed, otherwise {@code false}.
     */
    protected boolean traversable(Position current, Direction direction) {
        return collisionManager.traversable(current, EntityType.PLAYER, direction, true);
    }
}
