package io.luna.game.model.path;

import io.luna.game.model.Direction;
import io.luna.game.model.EntityType;
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoordinateHashPathTest {
    /** A coordinate key with the old packed hash, for comparison using the same A* implementation. */
    private record LegacyPosition(Position position) implements Locatable {
        @Override public int hashCode() {
            return (position.getZ() << 28) | ((getX() & 0x3fff) << 14) | (getY() & 0x3fff);
        }
        @Override public int getX() { return position.getX(); }
        @Override public int getY() { return position.getY(); }
        @Override public Position abs() { return position; }
        @Override public boolean contains(Position other) { return position.equals(other); }
    }

    private static boolean walkable(int x, int y, boolean gap) {
        return x >= 3200 && x < 3264 && y >= 3200 && y < 3264 &&
                (x != 3220 || (gap && y == 3230));
    }

    @Test
    void actualPlayerPathsMatchLegacyHashForCompletePartialAndEmptyRoutes() {
        for (int z = 0; z < 4; z++) for (boolean gap : new boolean[]{false, true}) {
            final int plane = z;
            CollisionManager collision = mock(CollisionManager.class);
            when(collision.traversable(any(Position.class), eq(EntityType.PLAYER), any(Direction.class), eq(true)))
                    .thenAnswer(invocation -> {
                        Position from = invocation.getArgument(0);
                        Direction direction = invocation.getArgument(2);
                        return walkable(from.getX() + direction.getTranslateX(),
                                from.getY() + direction.getTranslateY(), gap);
                    });
            AStarPathfinder<LegacyPosition> legacy = new AStarPathfinder<>(collision) {
                @Override public boolean isTraversable(LegacyPosition from, LegacyPosition to, Direction direction) {
                    return walkable(to.getX(), to.getY(), gap);
                }
                @Override public LegacyPosition createNeighbor(int x, int y) {
                    return new LegacyPosition(new Position(x, y, plane));
                }
                @Override public Heuristic getHeuristic() { return Heuristic.CHEBYSHEV; }
            };
            PlayerPathfinder mixed = new PlayerPathfinder(collision, plane);
            Position start = new Position(3205, 3205, plane);
            for (Position target : new Position[]{new Position(3240, 3240, plane), start}) {
                PathResult<LegacyPosition> before = legacy.find(new LegacyPosition(start), new LegacyPosition(target));
                PathResult<Position> after = mixed.find(start, target);
                assertEquals(before.getType(), after.getType());
                assertEquals(before.getPath().stream().map(LegacyPosition::abs).toList(),
                        after.getPath().stream().toList());
                assertEquals(target.equals(start) ? PathResultType.EMPTY :
                        gap ? PathResultType.COMPLETE : PathResultType.PARTIAL, after.getType());
            }
        }
    }
}
