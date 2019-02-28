package io.luna.game.model;

import io.luna.game.model.mob.Player;
import org.junit.Test;

/**
 * A test that ensures that functions within {@link Position} are functioning correctly.
 *
 * @author lare96 <http://github.com/lare96>
 */
final class AreaTest {

    /**
     * Ensures the north east {@code x} coordinate cannot be smaller than the south west {@code x} coordinate.
     */
    @Test(expected = IllegalArgumentException.class)
    void testNorthEastX() {
        newArea(0, 0, -1, 0);
    }

    /**
     * Ensures the north east {@code y} coordinate cannot be smaller than the south west {@code y} coordinate.
     */
    @Test(expected = IllegalArgumentException.class)
    void testNorthEastY() {
        newArea(0, 0, 0, -1);
    }

    /**
     * Constructs a new {@link Area} for this test.
     */
    private void newArea(int swX, int swY, int neX, int neY) {
        new Area(swX, swY, neX, neY) {
            @Override
            public void enter(Player player) {

            }

            @Override
            public void exit(Player player) {

            }
        };
    }
}