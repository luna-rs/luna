package io.luna.game.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link AreaTest}.
 *
 * @author lare96 
 */
final class AreaTest {

    @Test
    void northEastX() {
        assertThrows(IllegalArgumentException.class, () -> Area.of(0, 0, -1, 0));
    }

    @Test
    void northEastY() {
        assertThrows(IllegalArgumentException.class, () -> Area.of(0, 0, 0, -1));
    }
}