package io.luna.game.model.path;

import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A minimal, fast pathfinder that performs straight-line walking toward a target until blocked.
 * <p>
 * {@link SimplePathfinder} is not a true shortest-path algorithm. Instead, it acts as a low-cost fallback that
 * attempts to walk horizontally toward the target first, then vertically, recursively alternating directions when
 * possible. If a step becomes blocked at any point, the path constructed up to that moment is returned.
 * </p>
 *
 * <h3>Characteristics</h3>
 * <ul>
 *     <li><strong>No diagonals:</strong> Movement is axis-aligned only.</li>
 *     <li><strong>No backtracking:</strong> Once blocked, the algorithm does not attempt detours.</li>
 *     <li><strong>Extremely cheap:</strong> Runs in linear time relative to the horizontal/vertical gap.</li>
 *     <li><strong>Non-optimal:</strong> Useful only for predictable, low-cost or fallback movement.</li>
 * </ul>
 * <p>
 * If the desired destination is unreachable by simple horizontal/vertical stepping, the algorithm terminates early
 * and returns the partial path.
 * </p>
 *
 * @author Major
 */
public final class SimplePathfinder extends GamePathfinder<Position> {

    /**
     * Creates a new {@code SimplePathfinder}.
     *
     * @param collisionManager The {@link CollisionManager} used to determine whether cardinal
     * steps are traversable.
     */
    public SimplePathfinder(CollisionManager collisionManager) {
        super(collisionManager);
    }

    @Override
    public Deque<Position> find(Position origin, Position target) {
        Deque<Position> positions = new ArrayDeque<>((int) (origin.computeLongestDistance(target) * 1.5));
        return addHorizontal(origin, target, positions);
    }

    /**
     * Attempts to walk horizontally (east or west) toward the target.
     * <p>
     * Once horizontal alignment is reached or a horizontal step becomes blocked, the method will attempt to walk
     * vertically if possible. Horizontal and vertical calls alternate recursively until progress is no longer possible.
     * </p>
     *
     * @param start The starting position for this segment.
     * @param target The goal position.
     * @param positions The deque into which visited positions are appended.
     * @return The updated deque containing the partial or complete path.
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
        if (!last.equals(target)
                && dy != 0
                && traversable(last, dy > 0 ? Direction.SOUTH : Direction.NORTH)) {
            return addVertical(last, target, positions);
        }

        return positions;
    }

    /**
     * Attempts to walk vertically (north or south) toward the target.
     * <p>
     * Once vertical alignment is reached or a vertical step becomes blocked, the method will attempt to walk
     * horizontally if possible. Horizontal and vertical calls alternate recursively until progress is no longer
     * possible.
     * </p>
     *
     * @param start The starting position for this segment.
     * @param target The goal position.
     * @param positions The deque into which visited positions are appended.
     * @return The updated deque containing the partial or complete path.
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
        if (!last.equals(target)
                && dx != 0
                && traversable(last, dx > 0 ? Direction.WEST : Direction.EAST)) {
            return addHorizontal(last, target, positions);
        }

        return positions;
    }
}
