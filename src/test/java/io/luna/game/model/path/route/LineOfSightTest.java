package io.luna.game.model.path.route;

import org.junit.jupiter.api.Test;

import static io.luna.game.model.collision.CollisionFlag.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LineOfSight}.
 *
 * @author hydrozoa
 */
final class LineOfSightTest {

    private final TestMap map = new TestMap();

    private boolean sight(int srcX, int srcY, int destX, int destY) {
        return LineOfSight.hasLineOfSight(map.view(), 0, srcX, srcY, destX, destY);
    }

    private boolean walk(int srcX, int srcY, int destX, int destY) {
        return LineOfSight.hasLineOfWalk(map.view(), 0, srcX, srcY, destX, destY, 1, 1, 1, 1, 0);
    }

    @Test
    void testOpenGroundIsClearInEveryDirection() {
        int[][] targets = {{110, 100}, {90, 100}, {100, 110}, {100, 90}, {110, 110}, {90, 90}, {110, 90}, {90, 110},
                {110, 103}, {103, 110}, {95, 108}};
        for (int[] target : targets) {
            assertTrue(sight(100, 100, target[0], target[1]), target[0] + "," + target[1]);
            assertTrue(walk(100, 100, target[0], target[1]), target[0] + "," + target[1]);
        }
    }

    @Test
    void testSameTile() {
        map.flag(100, 100, LOC | LOC_PROJ_BLOCKER);
        assertTrue(sight(100, 100, 100, 100));
    }

    @Test
    void testObjectThatStopsProjectilesBlocksSight() {
        map.flag(105, 100, LOC | LOC_PROJ_BLOCKER);
        assertFalse(sight(100, 100, 110, 100));
        assertFalse(sight(110, 100, 100, 100));
        assertTrue(sight(100, 101, 110, 101));
    }

    @Test
    void testObjectThatDoesNotStopProjectilesDoesNotBlockSight() {
        map.flag(105, 100, LOC);
        assertTrue(sight(100, 100, 110, 100));
        assertFalse(walk(100, 100, 110, 100));
    }

    @Test
    void testWallThatStopsProjectilesBlocksSightThroughItsSide() {
        // A wall on the north edge of the row y = 100.
        for (int x = 95; x <= 110; x++) {
            map.flag(x, 100, WALL_NORTH | WALL_NORTH_PROJ_BLOCKER);
            map.flag(x, 101, WALL_SOUTH | WALL_SOUTH_PROJ_BLOCKER);
        }
        assertFalse(sight(100, 99, 100, 103));
        assertFalse(sight(103, 103, 100, 99));
        assertTrue(sight(100, 101, 100, 103));
        assertTrue(sight(100, 100, 105, 100));
    }

    @Test
    void testWallThatDoesNotStopProjectilesStillStopsWalking() {
        for (int x = 95; x <= 110; x++) {
            map.flag(x, 100, WALL_NORTH);
            map.flag(x, 101, WALL_SOUTH);
        }
        assertTrue(sight(100, 99, 100, 103));
        assertFalse(walk(100, 99, 100, 103));
    }

    @Test
    void testStandingInsideAnObjectBlocksSight() {
        map.flag(100, 100, LOC | LOC_PROJ_BLOCKER);
        assertFalse(sight(100, 100, 106, 100));
    }

    @Test
    void testTheTargetTileItselfDoesNotBlockSight() {
        // Something that stops projectiles can still be seen and shot at.
        map.flag(105, 100, LOC | LOC_PROJ_BLOCKER);
        assertTrue(sight(100, 100, 105, 100));
        assertTrue(sight(100, 103, 105, 100));
    }

    @Test
    void testBlockedTerrainStopsWalkingButNotSight() {
        map.flag(105, 100, BLOCK_WALK);
        assertTrue(sight(100, 100, 110, 100));
        assertFalse(walk(100, 100, 110, 100));
    }

    @Test
    void testLargeViewersStartFromTheirClosestTile() {
        // A wall just south of a size 3 viewer is not between it and a target to the north.
        map.flag(101, 99, LOC | LOC_PROJ_BLOCKER);
        assertTrue(LineOfSight.hasLineOfSight(map.view(), 0, 100, 100, 101, 110, 3, 3, 1, 1, 0));
    }

    @Test
    void testExtraFlagBlocksSight() {
        map.flag(105, 100, BLOCK_NPCS);
        assertTrue(sight(100, 100, 110, 100));
        assertFalse(LineOfSight.hasLineOfSight(map.view(), 0, 100, 100, 110, 100, 1, 1, 1, 1, BLOCK_NPCS));
    }
}
