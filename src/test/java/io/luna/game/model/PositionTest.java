package io.luna.game.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link Position}.
 *
 * @author lare96 <http://github.com/lare96>
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
        assertThrows(IllegalArgumentException.class, () -> new Position(1, 1, 4));
    }

    @Test
    void invalidLowerZ() {
        assertThrows(IllegalArgumentException.class, () -> new Position(1, 1, -1));
    }
}
