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

}
