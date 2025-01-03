package io.luna.game.model.path;

import com.google.common.base.Preconditions;
import io.luna.game.model.Area;
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
 */
abstract class PathfindingAlgorithm {

    private final CollisionManager collisionManager;

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
     * @param directions The Directions that should be checked.
     * @return {@code true} if any of the Directions lead to a traversable tile, otherwise {@code false}.
     */
    protected boolean traversable(Position current, Direction... directions) {
        return traversable(current, Optional.empty(), directions);
    }

    /**
     * Returns whether or not a {@link Position} walking one step in any of the specified {@link Direction}s would lead
     * to is traversable.
     *
     * @param current The current Position.
     * @param area The {@link Optional} containing the Area boundary.
     * @param directions The Directions that should be checked.
     * @return {@code true} if any of the Directions lead to a traversable tile, otherwise {@code false}.
     */
    protected boolean traversable(Position current, Optional<Area> area, Direction... directions) {
        Preconditions.checkArgument(directions != null && directions.length > 0, "Directions array cannot be null.");
        int height = current.getZ();

        Area boundary = area.orElse(null);

        for (Direction direction : directions) {
            int x = current.getX(), y = current.getY();
            int value = direction.getId();

            if (value >= Direction.NORTH_WEST.getId() && value <= Direction.NORTH_EAST.getId()) {
                y++;
            } else if (value >= Direction.SOUTH_WEST.getId() && value <= Direction.SOUTH_EAST.getId()) {
                y--;
            }

            if (direction == Direction.NORTH_EAST || direction == Direction.EAST || direction == Direction.SOUTH_EAST) {
                x++;
            } else if (direction == Direction.NORTH_WEST || direction == Direction.WEST || direction == Direction.SOUTH_WEST) {
                x--;
            }

            if (collisionManager.traversable(current, EntityType.NPC, direction)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns whether or not the specified {@link Position} is inside the specified {@code boundary}.
     *
     * @param position The Position.
     * @param boundary The boundary Positions.
     * @return {@code true} if the specified Position is inside the boundary, {@code false} if not.
     */
    private boolean inside(Position position, Position[] boundary) {
        int x = position.getX(), y = position.getY();
        Position min = boundary[0], max = boundary[1];

        return x >= min.getX() && y >= min.getY() && x <= max.getX() && y <= max.getY();
    }

}