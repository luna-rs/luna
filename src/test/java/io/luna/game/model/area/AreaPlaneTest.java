package io.luna.game.model.area;

import io.luna.game.model.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.Point;
import java.util.HashSet;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks wildcard containment while keeping generated area positions on a valid representative plane.
 *
 * @author lare96
 */
final class AreaPlaneTest {

    private static final List<Point> VERTICES = List.of(
            new Point(100, 100), new Point(110, 100), new Point(110, 110), new Point(100, 110));

    private static Stream<Arguments> shapes() {
        return Stream.of(
                Arguments.of("box", (IntFunction<Area>) plane -> Area.of(100, 100, 110, 110, plane)),
                Arguments.of("polygon", (IntFunction<Area>) plane -> Area.of(VERTICES, plane)),
                Arguments.of("circle", (IntFunction<Area>) plane -> Area.of(new Point(105, 105), 5, plane)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shapes")
    void wildcardContainsEveryPlaneWithoutExpandingTheFootprint(String shape, IntFunction<Area> factory) {
        Area area = factory.apply(-1);

        assertEquals(-1, area.getPlane());
        for (int plane = 0; plane < 4; plane++) {
            assertTrue(area.contains(new Position(105, 105, plane)));
            assertFalse(area.contains(new Position(90, 105, plane)));
            assertFalse(area.contains(new Position(120, 105, plane)));
            assertFalse(area.contains(new Position(105, 90, plane)));
            assertFalse(area.contains(new Position(105, 120, plane)));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shapes")
    void explicitPlaneRemainsStrict(String shape, IntFunction<Area> factory) {
        Area area = factory.apply(2);

        assertEquals(2, area.getPlane());
        for (int plane = 0; plane < 4; plane++) {
            assertEquals(plane == 2, area.contains(new Position(105, 105, plane)));
        }
        assertTrue(area.getPositions().stream().allMatch(position -> position.getZ() == 2));
        assertEquals(2, area.getAnchorPosition().getZ());
    }

    @Test
    void factoriesWithoutAPlaneStillUseGroundLevel() {
        List<Area> areas = List.of(
                Area.of(100, 100, 110, 110),
                Area.ofRadius(100, 100, 11, 11),
                Area.of(new Position(105, 105, 3), 5),
                Area.of(VERTICES),
                Area.of(new Point(105, 105), 5));

        for (Area area : areas) {
            assertEquals(0, area.getPlane());
            for (int plane = 0; plane < 4; plane++) {
                assertEquals(plane == 0, area.contains(new Position(105, 105, plane)));
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shapes")
    void wildcardPositionsAndAnchorsKeepTheGroundLevelFootprint(String shape, IntFunction<Area> factory) {
        Area area = factory.apply(-1);
        Area ground = factory.apply(0);

        assertFalse(area.getPositions().isEmpty());
        assertEquals(ground.getPositions(), area.getPositions());
        assertEquals(ground.size(), area.size());
        assertEquals(0, area.getAnchorPosition().getZ());
        assertEquals(0, area.abs().getZ());
        for (int attempt = 0; attempt < 16; attempt++) {
            Position sampled = area.randomPosition();
            assertEquals(0, sampled.getZ());
            assertTrue(area.contains(sampled));
        }

        area.setAnchorPosition(new Position(105, 105, 3));
        assertEquals(new Position(105, 105), area.getAnchorPosition());
        assertEquals(-1, area.getPlane());
    }

    @Test
    void wildcardBoxFactoriesKeepCornersCentersAndRegionsOnGroundLevel() {
        List<SimpleBoxArea> boxes = List.of(
                Area.of(100, 100, 110, 110, -1),
                Area.ofRadius(100, 100, 11, 11, -1),
                Area.of(new Position(105, 105, 3), 5, -1));
        SimpleBoxArea ground = Area.of(100, 100, 110, 110);

        for (SimpleBoxArea box : boxes) {
            assertEquals(-1, box.getPlane());
            assertEquals(ground.getSouthWest(), box.getSouthWest());
            assertEquals(ground.getSouthEast(), box.getSouthEast());
            assertEquals(ground.getNorthWest(), box.getNorthWest());
            assertEquals(ground.getNorthEast(), box.getNorthEast());
            assertEquals(ground.getCenterPosition(), box.getCenterPosition());
            assertEquals(ground.getTouchedRegions(), box.getTouchedRegions());
            assertEquals(0, box.getAnchorPosition().getZ());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shapes")
    void invalidPlanesAreRejected(String shape, IntFunction<Area> factory) {
        for (int plane : new int[]{-2, 4}) {
            assertThrows(IllegalArgumentException.class, () -> factory.apply(plane));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shapes")
    void wildcardEqualityRemainsDistinctFromAFixedPlane(String shape, IntFunction<Area> factory) {
        Area area = factory.apply(-1);
        Area equivalent = factory.apply(-1);
        Area ground = factory.apply(0);

        assertEquals(area, equivalent);
        assertEquals(area.hashCode(), equivalent.hashCode());
        assertNotEquals(area, ground);
        assertNotEquals(ground, area);
        assertEquals(2, new HashSet<>(List.of(area, equivalent, ground)).size());
    }
}
