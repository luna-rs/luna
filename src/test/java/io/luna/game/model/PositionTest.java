package io.luna.game.model;

import org.junit.Test;

/**
 * A test that ensures that functions within {@link Position} are functioning correctly.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PositionTest {

    /**
     * Test invalid {@code x} values.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidX() {
        new Position(-1, 1, 1);
    }

    /**
     * Test invalid {@code y} values.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidY() {
        new Position(1, -1, 1);
    }

    /**
     * Test invalid upper bound {@code z} values.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidUpperZ() {
        new Position(1, 1, 4);
    }

    /**
     * Test invalid lower bound {@code z} values.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidLowerZ() {
        new Position(1, 1, -1);
    }
}
