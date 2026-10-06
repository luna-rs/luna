package io.luna.game.model.collision;

import io.luna.game.model.Direction;
import io.luna.game.model.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.luna.game.model.collision.CollisionFlag.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CollisionMatrix}.
 *
 * @author hydrozoa
 */
final class CollisionMatrixTest {

    private static CollisionMatrix open() {
        return CollisionMatrix.createMatrices(1, 8, 8)[0];
    }

    private static boolean untraversableFromEveryDirection(CollisionMatrix matrix, int x, int y, EntityType type) {
        for (Direction direction : Direction.ALL_EXCEPT_NONE) {
            if (!matrix.untraversable(x, y, type, direction)) {
                return false;
            }
        }
        return true;
    }

    @Test
    void testOpenTileIsTraversable() {
        CollisionMatrix matrix = open();
        for (Direction direction : Direction.ALL_EXCEPT_NONE) {
            assertFalse(matrix.untraversable(3, 3, EntityType.PLAYER, direction));
            assertFalse(matrix.untraversable(3, 3, EntityType.PROJECTILE, direction));
        }
    }

    @Test
    void testBlockedFill() {
        CollisionMatrix matrix = CollisionMatrix.createMatrices(1, 8, 8, true)[0];
        assertEquals(-1, matrix.get(3, 3));
        assertTrue(untraversableFromEveryDirection(matrix, 3, 3, EntityType.PLAYER));
        assertTrue(matrix.isBlocked(3, 3, EntityType.PROJECTILE));
    }

    @Test
    void testBlockedFillSurvivesFlagAndClear() {
        CollisionMatrix matrix = CollisionMatrix.createMatrices(1, 8, 8, true)[0];
        matrix.flag(0, 0, BLOCK_NPCS);
        matrix.clear(0, 0, BLOCK_NPCS);
        assertEquals(-1, matrix.get(0, 0));
    }

    @Test
    void testEveryProjectileFlagIsDistinct() {
        // The old 16 bit flags lost PROJECTILE_WEST because it overflowed a short.
        for (int flag : WALL_PROJ_BLOCKERS) {
            CollisionMatrix matrix = open();
            matrix.flag(2, 2, flag);
            assertEquals(1, Integer.bitCount(matrix.get(2, 2)));
        }
    }

    @Test
    void testWallBlocksEnteringFromItsSideOnly() {
        CollisionMatrix matrix = open();
        matrix.flag(1, 1, WALL_EAST);
        assertTrue(matrix.untraversable(1, 1, EntityType.PLAYER, Direction.WEST));
        assertFalse(matrix.untraversable(1, 1, EntityType.PLAYER, Direction.EAST));
        assertTrue(matrix.untraversable(1, 1, EntityType.PLAYER, Direction.SOUTH_WEST));
        assertFalse(matrix.untraversable(1, 1, EntityType.PROJECTILE, Direction.WEST));
    }

    @Test
    void testLocBlocksEverySide() {
        CollisionMatrix matrix = open();
        matrix.flag(2, 2, LOC | LOC_PROJ_BLOCKER);
        assertTrue(untraversableFromEveryDirection(matrix, 2, 2, EntityType.PLAYER));
        assertTrue(untraversableFromEveryDirection(matrix, 2, 2, EntityType.PROJECTILE));
    }

    @Test
    void testBlockedTerrainStopsMobsButNotProjectiles() {
        CollisionMatrix matrix = open();
        matrix.flag(2, 2, BLOCK_WALK);
        assertTrue(untraversableFromEveryDirection(matrix, 2, 2, EntityType.PLAYER));
        assertTrue(untraversableFromEveryDirection(matrix, 2, 2, EntityType.NPC));
        assertFalse(matrix.isBlocked(2, 2, EntityType.PROJECTILE));
    }

    @Test
    void testRoofBlocksNothing() {
        CollisionMatrix matrix = open();
        matrix.flag(4, 4, ROOF);
        assertTrue(matrix.get(4, 4) < 0);
        assertTrue(matrix.flagged(4, 4, ROOF));
        assertFalse(matrix.isBlocked(4, 4, EntityType.PLAYER));
        assertFalse(matrix.isBlocked(4, 4, EntityType.NPC));
        assertFalse(matrix.isBlocked(4, 4, EntityType.PROJECTILE));
    }

    @Test
    void testNpcTilesStopNpcsOnly() {
        CollisionMatrix matrix = open();
        matrix.flag(5, 5, BLOCK_NPCS);
        assertTrue(matrix.untraversable(5, 5, EntityType.NPC, Direction.EAST));
        assertFalse(matrix.untraversable(5, 5, EntityType.PLAYER, Direction.EAST));
        assertFalse(matrix.untraversable(5, 5, EntityType.PROJECTILE, Direction.EAST));
    }

    @Test
    void testPlayerTilesStopNpcsOnly() {
        CollisionMatrix matrix = open();
        matrix.flag(6, 6, BLOCK_PLAYERS);
        assertTrue(matrix.untraversable(6, 6, EntityType.NPC, Direction.NORTH_WEST));
        assertFalse(matrix.untraversable(6, 6, EntityType.PLAYER, Direction.NORTH_WEST));
        assertFalse(matrix.untraversable(6, 6, EntityType.PROJECTILE, Direction.NORTH_WEST));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void testFlagStaysUntilTheLastHolderClearsIt(int holders) {
        CollisionMatrix matrix = open();
        for (int i = 0; i < holders; i++) {
            matrix.flag(2, 2, WALL_NORTH);
        }
        for (int i = 0; i < holders - 1; i++) {
            matrix.clear(2, 2, WALL_NORTH);
            assertTrue(matrix.flagged(2, 2, WALL_NORTH));
        }
        matrix.clear(2, 2, WALL_NORTH);
        assertFalse(matrix.flagged(2, 2, WALL_NORTH));
    }

    @Test
    void testOverlapIsCountedPerFlag() {
        CollisionMatrix matrix = open();
        matrix.flag(3, 3, WALL_NORTH | LOC);
        matrix.flag(3, 3, WALL_NORTH);
        matrix.clear(3, 3, WALL_NORTH | LOC);
        assertTrue(matrix.flagged(3, 3, WALL_NORTH));
        assertFalse(matrix.flagged(3, 3, LOC));
        matrix.clear(3, 3, WALL_NORTH);
        assertEquals(0, matrix.get(3, 3));
    }

    @Test
    void testResetDropsOverlap() {
        CollisionMatrix matrix = open();
        matrix.flag(4, 4, LOC);
        matrix.flag(4, 4, LOC);
        matrix.reset();
        matrix.flag(4, 4, LOC);
        matrix.clear(4, 4, LOC);
        assertEquals(0, matrix.get(4, 4));
    }

    @Test
    void testCopyIsIndependent() {
        CollisionMatrix matrix = open();
        CollisionMatrix copy = matrix.copy();
        matrix.flag(0, 0, LOC);
        assertEquals(0, copy.get(0, 0));
        assertEquals(LOC, matrix.get(0, 0));
    }
}
