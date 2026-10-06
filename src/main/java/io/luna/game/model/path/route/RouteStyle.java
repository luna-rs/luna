package io.luna.game.model.path.route;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The shape of the routes that a {@link RouteFinder} comes up with.
 * <p>
 * There is usually more than one route of the shortest length between two tiles: a route can turn early or late, and
 * take its diagonal steps first or last. All of them are as short as each other, so this only changes which one is
 * picked, by changing the order in which the search tries the directions out of a tile. The routes are always valid.
 *
 * @author hydrozoa
 */
public enum RouteStyle {

    /**
     * The routes of the real game: west, east, south, north, and then the four diagonals.
     */
    STANDARD {
        @Override
        int[] order() {
            return new int[]{0, 1, 2, 3, 4, 5, 6, 7};
        }
    },

    /**
     * Routes that take their diagonal steps first, which makes them turn early.
     */
    DIAGONAL_FIRST {
        @Override
        int[] order() {
            return new int[]{4, 5, 6, 7, 0, 1, 2, 3};
        }
    },

    /**
     * Routes that try the directions in a different random order for every search.
     */
    RANDOM {
        @Override
        int[] order() {
            int[] order = {0, 1, 2, 3, 4, 5, 6, 7};
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = order.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int swap = order[i];
                order[i] = order[j];
                order[j] = swap;
            }
            return order;
        }
    };

    /**
     * Returns the order the search tries the directions out of a tile in. The numbers are indices into the directions
     * of a {@link RouteFinder}: west, east, south, north, south west, south east, north west and north east.
     *
     * @return The order, which has every direction once.
     */
    abstract int[] order();
}
