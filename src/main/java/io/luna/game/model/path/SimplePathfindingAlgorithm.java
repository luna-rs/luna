package io.luna.game.model.path;

import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A very simple pathfinding algorithm that simply walks in the direction of the target until it either reaches it or is
 * blocked.
 *
 * @author Major
 * @author lare96
 */
public final class SimplePathfindingAlgorithm extends PathfindingAlgorithm {

    /**
     * Creates the SimplePathfindingAlgorithm.
     *
     * @param collisionManager The {@link CollisionManager} used to check if there is a collision
     * between two {@link Position}s in a path.
     */
    public SimplePathfindingAlgorithm(CollisionManager collisionManager) {
        super(collisionManager);
    }

    @Override
    public Deque<Position> find(Position origin, Position target) {
        int approximation = (int) (origin.computeLongestDistance(target) * 1.5);
        Deque<Position> positions = new ArrayDeque<>(approximation);

        return addHorizontal(origin, target, positions);
    }

    /**
     * Adds the necessary and possible horizontal {@link Position}s to the existing {@link Deque}.
     * <p/>
     * This method:
     * <ul>
     * <li>Adds positions horizontally until we are either horizontally aligned with the target, or the next step is not
     * traversable.
     * <li>Checks if we are not at the target, and that either of the horizontally-adjacent positions are traversable:
     * if so, we traverse horizontally (see {@link #addHorizontal}); if not, return the current path.
     * </ul>
     *
     * @param start The current position.
     * @param target The target position.
     * @param positions The deque of positions.
     * @return The deque of positions containing the path.
     */
    private Deque<Position> addHorizontal(Position start, Position target, Deque<Position> positions) {
        int x = start.getX(), y = start.getY(), height = start.getZ();
        int dx = x - target.getX(), dy = y - target.getY();

        if (dx > 0) {
            Position current = start;

            while (traversable(current, Direction.WEST) && dx-- > 0) {
                current = new Position(--x, y, height);
                positions.addLast(current);
            }
        } else if (dx < 0) {
            Position current = start;

            while (traversable(current, Direction.EAST) && dx++ < 0) {
                current = new Position(++x, y, height);
                positions.addLast(current);
            }
        }

        Position last = new Position(x, y, height);
        if (!last.equals(target) && dy != 0 && traversable(last, dy > 0 ? Direction.SOUTH : Direction.NORTH)) {
            return addVertical(last, target, positions);
        }

        return positions;
    }

    /**
     * Adds the necessary and possible vertical {@link Position}s to the existing {@link Deque}.
     * <p/>
     * This method:
     * <ul>
     * <li>Adds positions vertically until we are either vertically aligned with the target, or the next step is not
     * traversable.
     * <li>Checks if we are not at the target, and that either of the horizontally-adjacent positions are traversable:
     * if so, we traverse horizontally (see {@link #addHorizontal}); if not, return the current path.
     * </ul>
     *
     * @param start The current position.
     * @param target The target position.
     * @param positions The deque of positions.
     * @return The deque of positions containing the path.
     */
    private Deque<Position> addVertical(Position start, Position target, Deque<Position> positions) {
        int x = start.getX(), y = start.getY(), height = start.getZ();
        int dy = y - target.getY(), dx = x - target.getX();

        if (dy > 0) {
            Position current = start;

            while (traversable(current, Direction.SOUTH) && dy-- > 0) {
                current = new Position(x, --y, height);
                positions.addLast(current);
            }
        } else if (dy < 0) {
            Position current = start;

            while (traversable(current, Direction.NORTH) && dy++ < 0) {
                current = new Position(x, ++y, height);
                positions.addLast(current);
            }
        }

        Position last = new Position(x, y, height);
        if (!last.equals(target) && dx != 0
                && traversable(last, dx > 0 ? Direction.WEST : Direction.EAST)) {
            return addHorizontal(last, target, positions);
        }

        return positions;
    }

}
