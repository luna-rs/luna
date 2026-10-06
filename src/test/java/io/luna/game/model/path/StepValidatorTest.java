package io.luna.game.model.path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.luna.game.model.collision.CollisionFlag.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StepValidator}.
 *
 * @author hydrozoa
 */
final class StepValidatorTest {

    private static final int[][] STEPS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};

    private final TestMap map = new TestMap();

    private boolean canTravel(int x, int y, int dx, int dy, int size) {
        return StepValidator.canTravel(map.view(), 0, x, y, dx, dy, size, 0, RouteStrategy.NORMAL);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5})
    void testOpenGroundAllowsEveryStep(int size) {
        for (int[] step : STEPS) {
            assertTrue(canTravel(100, 100, step[0], step[1], size), step[0] + "," + step[1]);
        }
    }

    @Test
    void testInvalidStep() {
        assertThrows(IllegalArgumentException.class, () -> canTravel(100, 100, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> canTravel(100, 100, 2, 0, 1));
    }

    @Test
    void testWallBlocksOnlyTheSideItIsOn() {
        // A wall on the west edge of (101, 100).
        map.flag(101, 100, WALL_WEST);
        map.flag(100, 100, WALL_EAST);

        assertFalse(canTravel(100, 100, 1, 0, 1));
        assertFalse(canTravel(101, 100, -1, 0, 1));
        assertTrue(canTravel(101, 100, 0, 1, 1));
        assertTrue(canTravel(100, 100, 0, 1, 1));
    }

    @Test
    void testDiagonalCannotCutACorner() {
        map.flag(101, 100, LOC);
        assertFalse(canTravel(100, 100, 1, 1, 1));
        assertTrue(canTravel(100, 100, 0, 1, 1));
        assertFalse(canTravel(100, 100, 1, 0, 1));
    }

    @Test
    void testBlockedTerrainAndGroundDecorationStopSteps() {
        map.flag(101, 100, BLOCK_WALK);
        map.flag(100, 101, GROUND_DECOR);
        assertFalse(canTravel(100, 100, 1, 0, 1));
        assertFalse(canTravel(100, 100, 0, 1, 1));
    }

    @Test
    void testLargeMobCannotUseAGapThatIsTooNarrow() {
        // A wall of objects with a one tile gap at y = 101.
        map.flag(105, 100, LOC);
        map.flag(105, 102, LOC);
        map.flag(105, 103, LOC);

        assertTrue(canTravel(104, 101, 1, 0, 1));
        assertFalse(canTravel(103, 100, 1, 0, 2));
        assertFalse(canTravel(103, 101, 1, 0, 2));
    }

    @Test
    void testLargeMobIsOnlyStoppedOnItsLeadingEdge() {
        // The mob covers (100, 100) to (101, 101). Its own tiles being solid must not matter.
        map.fill(100, 100, 2, 2, LOC);
        assertTrue(canTravel(100, 100, 1, 0, 2));
        assertTrue(canTravel(100, 100, 0, 1, 2));
        assertTrue(canTravel(100, 100, -1, 0, 2));

        map.flag(102, 101, LOC);
        assertFalse(canTravel(100, 100, 1, 0, 2));
    }

    @Test
    void testLargeMobMiddleTilesNeedToBeOpenOnEverySide() {
        // A wall that touches the leading edge of a size 3 mob between its two corners, from the south.
        map.flag(101, 99, WALL_NORTH);
        assertFalse(canTravel(100, 100, 0, -1, 3));
        assertTrue(canTravel(100, 100, 0, 1, 3));
    }

    @Test
    void testLargeDiagonalNeedsItsCornerAndEdges() {
        assertTrue(canTravel(100, 100, 1, 1, 3));
        map.flag(103, 103, LOC);
        assertFalse(canTravel(100, 100, 1, 1, 3));
        assertTrue(canTravel(100, 100, 1, 0, 3));
        assertTrue(canTravel(100, 100, 0, 1, 3));
    }

    @Test
    void testExtraFlagStopsAStep() {
        map.flag(101, 100, BLOCK_NPCS);
        assertTrue(canTravel(100, 100, 1, 0, 1));
        assertFalse(StepValidator.canTravel(map.view(), 0, 100, 100, 1, 0, 1, BLOCK_NPCS, RouteStrategy.NORMAL));
    }

    @Test
    void testBlockedStrategyOnlyWalksOnBlockedTerrain() {
        map.flag(101, 100, BLOCK_WALK);
        assertTrue(StepValidator.canTravel(map.view(), 0, 100, 100, 1, 0, 1, 0, RouteStrategy.BLOCKED));
        assertFalse(StepValidator.canTravel(map.view(), 0, 100, 100, 0, 1, 1, 0, RouteStrategy.BLOCKED));

        map.flag(102, 100, BLOCK_WALK | LOC);
        assertFalse(StepValidator.canTravel(map.view(), 0, 101, 100, 1, 0, 1, 0, RouteStrategy.BLOCKED));
    }

    @Test
    void testIndoorsStrategyNeedsARoof() {
        map.flag(101, 100, ROOF);
        assertTrue(StepValidator.canTravel(map.view(), 0, 100, 100, 1, 0, 1, 0, RouteStrategy.INDOORS));
        assertFalse(StepValidator.canTravel(map.view(), 0, 100, 100, 0, 1, 1, 0, RouteStrategy.INDOORS));
        assertTrue(StepValidator.canTravel(map.view(), 0, 100, 100, 0, 1, 1, 0, RouteStrategy.NORMAL));
    }

    @Test
    void testOutdoorsStrategyAvoidsRoofs() {
        map.flag(101, 100, ROOF);
        assertFalse(StepValidator.canTravel(map.view(), 0, 100, 100, 1, 0, 1, 0, RouteStrategy.OUTDOORS));
        assertTrue(StepValidator.canTravel(map.view(), 0, 100, 100, 0, 1, 1, 0, RouteStrategy.OUTDOORS));
    }

    @Test
    void testTilesOutsideTheMapAreBlocked() {
        assertFalse(StepValidator.canTravel(map.view(), 0, 0, 0, -1, 0, 1, 0, RouteStrategy.NORMAL));
        assertFalse(StepValidator.canTravel(map.view(), 0, 0, 0, 0, -1, 1, 0, RouteStrategy.NORMAL));
    }
}
