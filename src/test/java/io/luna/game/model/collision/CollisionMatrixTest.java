package io.luna.game.model.collision;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

final class CollisionMatrixTest {

    @ParameterizedTest
    @CsvSource({
            "-1, 0, 'X coordinate must be [0, 3), received -1. (-1) must not be negative'",
            "3, 0, 'X coordinate must be [0, 3), received 3. (3) must be less than size (3)'",
            "0, -1, 'Y coordinate must be [0, 2), received -1. (-1) must not be negative'",
            "0, 2, 'Y coordinate must be [0, 2), received 2. (2) must be less than size (2)'",
            "-1, -1, 'X coordinate must be [0, 3), received -1. (-1) must not be negative'"
    })
    void invalidCoordinatesPreserveExceptionMessage(int x, int y, String expectedMessage) {
        CollisionMatrix matrix = new CollisionMatrix(3, 2);
        var exception = assertThrows(IndexOutOfBoundsException.class, () -> matrix.get(x, y));
        assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    void validEdgeCoordinatesAddressDistinctTiles() {
        CollisionMatrix matrix = new CollisionMatrix(3, 2);
        matrix.flag(0, 0, CollisionFlag.MOB_NORTH);
        matrix.flag(2, 1, CollisionFlag.MOB_EAST);

        assertEquals(CollisionFlag.MOB_NORTH.asShort(), matrix.get(0, 0));
        assertEquals(CollisionFlag.MOB_EAST.asShort(), matrix.get(2, 1));
        assertEquals(0, matrix.get(2, 0));
        assertEquals(0, matrix.get(0, 1));
    }

    @Test
    void emptyMatrixRejectsCoordinatesWithBoundsMessage() {
        CollisionMatrix matrix = new CollisionMatrix(0, 0);
        var exception = assertThrows(IndexOutOfBoundsException.class, () -> matrix.get(0, 0));
        assertEquals("X coordinate must be [0, 0), received 0. (0) must be less than size (0)",
                exception.getMessage());
    }
}
