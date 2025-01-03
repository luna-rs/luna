package io.luna.game.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Unit tests for {@link Position}.
 *
 * @author lare96 
 */
final class PositionTest {

    @Test
    void invalidX() {
        assertThrows(IllegalArgumentException.class, () -> new Position(-1, 1, 1));
    }

    @Test
    void invalidY() {
        assertThrows(IllegalArgumentException.class, () -> new Position(1, -1, 1));
    }

    @Test
    void invalidUpperZ() {
        assertThrows(IllegalArgumentException.class, () -> new Position(1, 1, 4),
                "Z must be in range [0, 3] inclusively.");
    }

    @Test
    void invalidLowerZ() {
        assertThrows(IllegalArgumentException.class, () -> new Position(1, 1, -1));
    }

    @Test
    void testIsWithinDistance() {
        Position startPosition = new Position(0, 0, 0);

        // when X is in range, return true
        Position endPosition = new Position(1, 0, 0);
        assertTrue(startPosition.isWithinDistance(endPosition, 1));

        // when X is out of range, return false
        endPosition = endPosition.translate(1, 0);
        assertFalse(startPosition.isWithinDistance(endPosition, 1));

        // when Y is in range, return true
        endPosition = new Position(0, 1, 0);
        assertTrue(startPosition.isWithinDistance(endPosition, 1));

        // when Y is out of range, return false
        endPosition = endPosition.translate(0, 1);
        assertFalse(startPosition.isWithinDistance(endPosition, 1));

        // when start and end positions are on different planes, return false.
        endPosition = new Position(0, 0, 1);
        assertFalse(startPosition.isWithinDistance(endPosition, 1));
    }

    @Test
    void testIsViewable() {
        Position startPosition = new Position(0, 0, 0);

        // when position is at max view distance, return true
        Position endPosition = new Position(Position.VIEWING_DISTANCE, Position.VIEWING_DISTANCE, 0);
        assertTrue(startPosition.isViewable(endPosition));

        // when position is 1-unit out of view distance, return false
        endPosition = endPosition.translate(1, 1);
        assertFalse(startPosition.isViewable(endPosition));
    }

    @Test
    void testGetFarthestCoordinate() {
        Position startPosition = new Position(0, 0, 0);

        int farthestCoordinate = 1;
        int closestCoordinate = 0;

        Position endPosition = new Position(closestCoordinate, farthestCoordinate);
        int computed = startPosition.computeLongestDistance(endPosition);
        assertEquals(computed, farthestCoordinate);

        // swap X and Y coordinates
        endPosition = new Position(farthestCoordinate, closestCoordinate);
        computed = startPosition.computeLongestDistance(endPosition);
        assertEquals(computed, farthestCoordinate);
    }
}
