package io.luna.game.model.path;

import io.luna.game.model.Position;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.luna.game.model.collision.CollisionFlag.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RouteFinder}.
 *
 * @author hydrozoa
 */
final class RouteFinderTest {

    private final TestMap map = new TestMap();
    private final RouteFinder finder = new RouteFinder();

    private Route find(int startX, int startY, int destX, int destY) {
        return find(startX, startY, destX, destY, 1, 0, RouteStrategy.NORMAL, true);
    }

    private Route find(int startX, int startY, int destX, int destY, int size, int extraFlag, RouteStrategy strategy,
                       boolean moveNear) {
        return finder.find(map.view(), 0, startX, startY, destX, destY, size, extraFlag, strategy, moveNear, 255);
    }

    /**
     * Walks a route one tile at a time the way the walking queue does, and checks every step is allowed.
     *
     * @return The number of steps taken.
     */
    private int walk(Route route, int startX, int startY, int size, int extraFlag, RouteStrategy strategy) {
        int x = startX;
        int y = startY;
        int steps = 0;
        for (Position waypoint : route.getWaypoints()) {
            int deltaX = waypoint.getX() - x;
            int deltaY = waypoint.getY() - y;
            int count = Math.max(Math.abs(deltaX), Math.abs(deltaY));
            for (int i = 0; i < count; i++) {
                int stepX = Integer.signum(deltaX);
                int stepY = Integer.signum(deltaY);
                assertTrue(StepValidator.canTravel(map.view(), 0, x, y, stepX, stepY, size, extraFlag, strategy),
                        "blocked step at " + x + "," + y + " by " + stepX + "," + stepY);
                x += stepX;
                y += stepY;
                deltaX -= stepX;
                deltaY -= stepY;
                steps++;
            }
        }
        return steps;
    }

    @Test
    void testStraightRouteHasOneWaypoint() {
        Route route = find(100, 100, 108, 100);
        assertTrue(route.isSuccess());
        assertFalse(route.isAlternative());
        assertEquals(List.of(new Position(108, 100)), route.getWaypoints());
    }

    @Test
    void testDiagonalRouteHasOneWaypoint() {
        Route route = find(100, 100, 105, 105);
        assertEquals(List.of(new Position(105, 105)), route.getWaypoints());
    }

    @Test
    void testBendInTheRoute() {
        Route route = find(100, 100, 110, 103);
        assertTrue(route.isSuccess());
        assertEquals(new Position(110, 103), route.getWaypoints().get(route.getWaypoints().size() - 1));
        assertEquals(walk(route, 100, 100, 1, 0, RouteStrategy.NORMAL), 10);
    }

    @Test
    void testAlreadyThere() {
        Route route = find(100, 100, 100, 100);
        assertTrue(route.isSuccess());
        assertFalse(route.isAlternative());
        assertTrue(route.getWaypoints().isEmpty());
    }

    @Test
    void testRouteGoesAroundAWall() {
        map.fill(105, 95, 1, 11, LOC);
        Route route = find(100, 100, 110, 100);
        assertTrue(route.isSuccess());
        assertFalse(route.isAlternative());
        assertEquals(new Position(110, 100), route.getWaypoints().get(route.getWaypoints().size() - 1));
        assertTrue(walk(route, 100, 100, 1, 0, RouteStrategy.NORMAL) > 10);
    }

    @Test
    void testRouteGoesThroughAGap() {
        map.fill(105, 95, 1, 5, LOC);
        map.fill(105, 101, 1, 5, LOC);
        Route route = find(100, 100, 110, 100);
        assertFalse(route.isAlternative());
        assertEquals(10, walk(route, 100, 100, 1, 0, RouteStrategy.NORMAL));
    }

    @Test
    void testWallEdgesAreRespected() {
        // A wall along the north edge of a row of tiles: stepping across it is not allowed from either side.
        for (int x = 95; x < 106; x++) {
            map.flag(x, 100, WALL_NORTH);
            map.flag(x, 101, WALL_SOUTH);
        }
        Route route = find(100, 100, 100, 102);
        assertFalse(route.isAlternative());
        assertTrue(walk(route, 100, 100, 1, 0, RouteStrategy.NORMAL) > 2);
    }

    @Test
    void testUnreachableDestinationEndsAsCloseAsPossible() {
        // A closed box around the destination.
        map.fill(108, 108, 5, 1, LOC);
        map.fill(108, 112, 5, 1, LOC);
        map.fill(108, 109, 1, 3, LOC);
        map.fill(112, 109, 1, 3, LOC);
        Route route = find(100, 100, 110, 110);

        assertTrue(route.isSuccess());
        assertTrue(route.isAlternative());
        Position last = route.getWaypoints().get(route.getWaypoints().size() - 1);
        assertFalse(last.getX() == 110 && last.getY() == 110);
        // The closest reachable tiles are the ones right outside the box, 3 tiles from the destination.
        assertTrue(Math.abs(last.getX() - 110) <= 3 && Math.abs(last.getY() - 110) <= 3);
        walk(route, 100, 100, 1, 0, RouteStrategy.NORMAL);
    }

    @Test
    void testUnreachableDestinationFailsWhenNotMovingNear() {
        map.fill(108, 108, 5, 1, LOC);
        map.fill(108, 112, 5, 1, LOC);
        map.fill(108, 109, 1, 3, LOC);
        map.fill(112, 109, 1, 3, LOC);
        Route route = find(100, 100, 110, 110, 1, 0, RouteStrategy.NORMAL, false);
        assertFalse(route.isSuccess());
    }

    @Test
    void testDestinationOutsideTheSearchAreaFails() {
        Route route = find(100, 100, 400, 400);
        assertFalse(route.isSuccess());
    }

    @Test
    void testLargeMobMustFitInsideTheSearchArea() {
        // The search area reaches 63 tiles north of the start. A single tile fits at its edge, a size 3 mob does not.
        Route small = find(100, 100, 100, 163);
        assertFalse(small.isAlternative());
        assertEquals(new Position(100, 163), small.getWaypoints().get(small.getWaypoints().size() - 1));

        Route large = find(100, 100, 100, 163, 3, 0, RouteStrategy.NORMAL, true);
        assertTrue(large.isAlternative());
        assertTrue(large.getWaypoints().get(large.getWaypoints().size() - 1).getY() <= 100 + 64 - 3);
    }

    @Test
    void testLargeMobTakesAWiderRoute() {
        // A wall that is taller than the search area, with a gap that is one tile wide at y = 100.
        map.fill(105, 30, 1, 70, LOC);
        map.fill(105, 101, 1, 70, LOC);

        Route small = find(100, 100, 110, 100);
        assertFalse(small.isAlternative());
        assertEquals(10, walk(small, 100, 100, 1, 0, RouteStrategy.NORMAL));

        Route large = find(100, 100, 110, 100, 2, 0, RouteStrategy.NORMAL, true);
        assertTrue(large.isSuccess());
        assertTrue(large.isAlternative(), "a size 2 mob does not fit through the gap");
        walk(large, 100, 100, 2, 0, RouteStrategy.NORMAL);
    }

    @Test
    void testLargeMobFindsRoutesThroughWideGaps() {
        map.fill(105, 90, 1, 9, LOC);
        map.fill(105, 102, 1, 14, LOC);
        Route route = find(100, 100, 110, 100, 2, 0, RouteStrategy.NORMAL, true);
        assertFalse(route.isAlternative());
        assertEquals(10, walk(route, 100, 100, 2, 0, RouteStrategy.NORMAL));
    }

    @Test
    void testExtraFlagMakesTheRouteAvoidOtherMobs() {
        map.flag(105, 100, BLOCK_NPCS);
        Route plain = find(100, 100, 110, 100);
        assertEquals(10, walk(plain, 100, 100, 1, 0, RouteStrategy.NORMAL));

        Route avoiding = find(100, 100, 110, 100, 1, BLOCK_NPCS, RouteStrategy.NORMAL, true);
        assertFalse(avoiding.isAlternative());
        assertEquals(10, walk(avoiding, 100, 100, 1, BLOCK_NPCS, RouteStrategy.NORMAL));
        // It could not stay in a straight line, so it has more than one waypoint.
        assertTrue(avoiding.getWaypoints().size() > 1);
    }

    @Test
    void testIndoorsRouteStaysUnderTheRoof() {
        // A roofed room from (100, 100) to (105, 105), and an open doorway on the east side at y = 102.
        map.fill(100, 100, 6, 6, ROOF);
        for (int y = 100; y < 106; y++) {
            if (y != 102) {
                map.flag(106, y, LOC);
            }
        }
        Route inside = find(100, 100, 105, 105, 1, 0, RouteStrategy.INDOORS, true);
        assertFalse(inside.isAlternative());
        walk(inside, 100, 100, 1, 0, RouteStrategy.INDOORS);

        Route outside = find(100, 100, 108, 100, 1, 0, RouteStrategy.INDOORS, true);
        assertTrue(outside.isAlternative(), "the route can't leave the roof");
        Position last = outside.getWaypoints().get(outside.getWaypoints().size() - 1);
        assertTrue(last.getX() <= 105);
    }

    @Test
    void testWaypointLimit() {
        // Walls across the search area with gaps at alternating ends make the route zig zag.
        for (int row = 0; row < 4; row++) {
            int gapX = row % 2 == 0 ? 160 : 40;
            for (int x = 40; x <= 160; x++) {
                if (x != gapX) {
                    map.flag(x, 100 + row * 4, LOC);
                }
            }
        }

        Route full = finder.find(map.view(), 0, 100, 98, 100, 114, 1, 0, RouteStrategy.NORMAL, true, 255);
        assertTrue(full.isSuccess());
        assertFalse(full.isAlternative());
        assertTrue(full.getWaypoints().size() > 3);

        Route cut = finder.find(map.view(), 0, 100, 98, 100, 114, 1, 0, RouteStrategy.NORMAL, true, 3);
        assertEquals(3, cut.getWaypoints().size());
        assertEquals(full.getWaypoints().subList(0, 3), cut.getWaypoints());
    }

    @Test
    void testStandardStyleIsTheDefault() {
        map.fill(105, 95, 1, 11, LOC);
        Route byDefault = find(100, 100, 112, 104);
        Route standard = finder.find(map.view(), 0, 100, 100, 112, 104, 1, 0, RouteStrategy.NORMAL, true, 255,
                RouteStyle.STANDARD);
        assertEquals(byDefault.getWaypoints(), standard.getWaypoints());
    }

    @Test
    void testEveryStyleFindsAShortestRoute() {
        map.fill(105, 95, 1, 11, LOC);
        int shortest = walk(find(100, 100, 112, 104), 100, 100, 1, 0, RouteStrategy.NORMAL);

        for (RouteStyle style : RouteStyle.values()) {
            for (int i = 0; i < 25; i++) {
                Route route = finder.find(map.view(), 0, 100, 100, 112, 104, 1, 0, RouteStrategy.NORMAL, true, 255,
                        style);
                assertTrue(route.isSuccess());
                assertFalse(route.isAlternative());
                assertEquals(shortest, walk(route, 100, 100, 1, 0, RouteStrategy.NORMAL), style.toString());
            }
        }
    }

    @Test
    void testStylesGiveRoutesOfDifferentShapes() {
        java.util.Set<List<Position>> shapes = new java.util.HashSet<>();
        for (RouteStyle style : RouteStyle.values()) {
            for (int i = 0; i < 50; i++) {
                shapes.add(finder.find(map.view(), 0, 100, 100, 110, 103, 1, 0, RouteStrategy.NORMAL, true, 255, style)
                        .getWaypoints());
            }
        }
        assertTrue(shapes.size() > 1, "every route had the same shape");
    }

    @Test
    void testStylesStillRespectObstaclesAndLargeMobs() {
        map.fill(105, 30, 1, 70, LOC);
        map.fill(105, 102, 1, 70, LOC);
        for (RouteStyle style : RouteStyle.values()) {
            // A gap that is two tiles wide at y = 100 and y = 101.
            Route route = finder.find(map.view(), 0, 100, 100, 110, 100, 2, 0, RouteStrategy.NORMAL, true, 255, style);
            assertFalse(route.isAlternative(), style.toString());
            assertEquals(10, walk(route, 100, 100, 2, 0, RouteStrategy.NORMAL), style.toString());
        }
    }

    @Test
    void testFinderCanBeReused() {
        Route first = find(100, 100, 106, 100);
        Route second = find(100, 100, 100, 106);
        Route third = find(100, 100, 106, 100);
        assertEquals(first.getWaypoints(), third.getWaypoints());
        assertEquals(List.of(new Position(100, 106)), second.getWaypoints());
    }
}
