package io.luna.game.model.path;

import io.luna.game.model.collision.CollisionView;

import static io.luna.game.model.collision.CollisionFlag.*;

/**
 * Decides whether a mob of any size may take a single step.
 * <p>
 * A step is allowed when every tile the mob would move onto lets it in from the direction it is coming from. Only the
 * tiles on the leading edge of the mob are looked at, never the ones it already covers. The corners of that edge are
 * tested as diagonal moves, and the tiles between the corners are tested against every side but the one they are
 * entered from, so that a large mob cannot squeeze through a gap that is too narrow for it.
 * <p>
 * The rules follow rsmod's routefinder, which emulates the way the real game walks mobs.
 *
 * @author hydrozoa
 */
public final class StepValidator {

    /**
     * Determines if a mob may step from a tile to the tile next to it.
     *
     * @param view The collision flags to read.
     * @param level The height level.
     * @param x The absolute x coordinate of the south west tile the mob covers.
     * @param y The absolute y coordinate of the south west tile the mob covers.
     * @param offsetX The x direction of the step, {@code -1}, {@code 0} or {@code 1}.
     * @param offsetY The y direction of the step, {@code -1}, {@code 0} or {@code 1}.
     * @param size The width and length of the mob, in tiles.
     * @param extraFlag Flags that also stop the step, such as {@code BLOCK_NPCS}.
     * @param strategy The rule for moving onto a tile.
     * @return {@code true} if the step may be taken.
     * @throws IllegalArgumentException If the offsets are not a single step.
     */
    public static boolean canTravel(CollisionView view, int level, int x, int y, int offsetX, int offsetY, int size,
                                    int extraFlag, RouteStrategy strategy) {
        Walker walker = new Walker(view, level, extraFlag, strategy);
        boolean blocked;
        if (offsetX == 0 && offsetY == -1) {
            blocked = walker.south(x, y, size);
        } else if (offsetX == 0 && offsetY == 1) {
            blocked = walker.north(x, y, size);
        } else if (offsetX == -1 && offsetY == 0) {
            blocked = walker.west(x, y, size);
        } else if (offsetX == 1 && offsetY == 0) {
            blocked = walker.east(x, y, size);
        } else if (offsetX == -1 && offsetY == -1) {
            blocked = walker.southWest(x, y, size);
        } else if (offsetX == -1 && offsetY == 1) {
            blocked = walker.northWest(x, y, size);
        } else if (offsetX == 1 && offsetY == -1) {
            blocked = walker.southEast(x, y, size);
        } else if (offsetX == 1 && offsetY == 1) {
            blocked = walker.northEast(x, y, size);
        } else {
            throw new IllegalArgumentException("Invalid step offset: " + offsetX + ", " + offsetY);
        }
        return !blocked;
    }

    /**
     * The inputs shared by every check of one step.
     */
    private static final class Walker {

        private final CollisionView view;
        private final int level;
        private final int extraFlag;
        private final RouteStrategy strategy;

        Walker(CollisionView view, int level, int extraFlag, RouteStrategy strategy) {
            this.view = view;
            this.level = level;
            this.extraFlag = extraFlag;
            this.strategy = strategy;
        }

        /**
         * Determines if the mob may move onto the tile at (x, y) under the given block flags.
         */
        private boolean open(int x, int y, int blockFlags) {
            return strategy.canMove(view.get(x, y, level), blockFlags | extraFlag);
        }

        boolean south(int x, int y, int size) {
            if (size == 1) {
                return !open(x, y - 1, BLOCK_SOUTH);
            }
            if (!open(x, y - 1, BLOCK_SOUTH_WEST) || !open(x + size - 1, y - 1, BLOCK_SOUTH_EAST)) {
                return true;
            }
            for (int midX = x + 1; midX < x + size - 1; midX++) {
                if (!open(midX, y - 1, BLOCK_NORTH_EAST_AND_WEST)) {
                    return true;
                }
            }
            return false;
        }

        boolean north(int x, int y, int size) {
            if (size == 1) {
                return !open(x, y + 1, BLOCK_NORTH);
            }
            if (!open(x, y + size, BLOCK_NORTH_WEST) || !open(x + size - 1, y + size, BLOCK_NORTH_EAST)) {
                return true;
            }
            for (int midX = x + 1; midX < x + size - 1; midX++) {
                if (!open(midX, y + size, BLOCK_SOUTH_EAST_AND_WEST)) {
                    return true;
                }
            }
            return false;
        }

        boolean west(int x, int y, int size) {
            if (size == 1) {
                return !open(x - 1, y, BLOCK_WEST);
            }
            if (!open(x - 1, y, BLOCK_SOUTH_WEST) || !open(x - 1, y + size - 1, BLOCK_NORTH_WEST)) {
                return true;
            }
            for (int midY = y + 1; midY < y + size - 1; midY++) {
                if (!open(x - 1, midY, BLOCK_NORTH_AND_SOUTH_EAST)) {
                    return true;
                }
            }
            return false;
        }

        boolean east(int x, int y, int size) {
            if (size == 1) {
                return !open(x + 1, y, BLOCK_EAST);
            }
            if (!open(x + size, y, BLOCK_SOUTH_EAST) || !open(x + size, y + size - 1, BLOCK_NORTH_EAST)) {
                return true;
            }
            for (int midY = y + 1; midY < y + size - 1; midY++) {
                if (!open(x + size, midY, BLOCK_NORTH_AND_SOUTH_WEST)) {
                    return true;
                }
            }
            return false;
        }

        boolean southWest(int x, int y, int size) {
            if (size == 1) {
                return !open(x - 1, y - 1, BLOCK_SOUTH_WEST) || !open(x - 1, y, BLOCK_WEST) ||
                        !open(x, y - 1, BLOCK_SOUTH);
            }
            if (size == 2) {
                return !open(x - 1, y, BLOCK_NORTH_AND_SOUTH_EAST) || !open(x - 1, y - 1, BLOCK_SOUTH_WEST) ||
                        !open(x, y - 1, BLOCK_NORTH_EAST_AND_WEST);
            }
            if (!open(x - 1, y - 1, BLOCK_SOUTH_WEST)) {
                return true;
            }
            for (int mid = 1; mid < size; mid++) {
                if (!open(x - 1, y + mid - 1, BLOCK_NORTH_AND_SOUTH_EAST) ||
                        !open(x + mid - 1, y - 1, BLOCK_NORTH_EAST_AND_WEST)) {
                    return true;
                }
            }
            return false;
        }

        boolean northWest(int x, int y, int size) {
            if (size == 1) {
                return !open(x - 1, y + 1, BLOCK_NORTH_WEST) || !open(x - 1, y, BLOCK_WEST) ||
                        !open(x, y + 1, BLOCK_NORTH);
            }
            if (size == 2) {
                return !open(x - 1, y + 1, BLOCK_NORTH_AND_SOUTH_EAST) || !open(x - 1, y + 2, BLOCK_NORTH_WEST) ||
                        !open(x, y + 2, BLOCK_SOUTH_EAST_AND_WEST);
            }
            if (!open(x - 1, y + size, BLOCK_NORTH_WEST)) {
                return true;
            }
            for (int mid = 1; mid < size; mid++) {
                if (!open(x - 1, y + mid, BLOCK_NORTH_AND_SOUTH_EAST) ||
                        !open(x + mid - 1, y + size, BLOCK_SOUTH_EAST_AND_WEST)) {
                    return true;
                }
            }
            return false;
        }

        boolean southEast(int x, int y, int size) {
            if (size == 1) {
                return !open(x + 1, y - 1, BLOCK_SOUTH_EAST) || !open(x + 1, y, BLOCK_EAST) ||
                        !open(x, y - 1, BLOCK_SOUTH);
            }
            if (size == 2) {
                return !open(x + 1, y - 1, BLOCK_NORTH_EAST_AND_WEST) || !open(x + 2, y - 1, BLOCK_SOUTH_EAST) ||
                        !open(x + 2, y, BLOCK_NORTH_AND_SOUTH_WEST);
            }
            if (!open(x + size, y - 1, BLOCK_SOUTH_EAST)) {
                return true;
            }
            for (int mid = 1; mid < size; mid++) {
                if (!open(x + size, y + mid - 1, BLOCK_NORTH_AND_SOUTH_WEST) ||
                        !open(x + mid, y - 1, BLOCK_NORTH_EAST_AND_WEST)) {
                    return true;
                }
            }
            return false;
        }

        boolean northEast(int x, int y, int size) {
            if (size == 1) {
                return !open(x + 1, y + 1, BLOCK_NORTH_EAST) || !open(x + 1, y, BLOCK_EAST) ||
                        !open(x, y + 1, BLOCK_NORTH);
            }
            if (size == 2) {
                return !open(x + 1, y + 2, BLOCK_SOUTH_EAST_AND_WEST) || !open(x + 2, y + 2, BLOCK_NORTH_EAST) ||
                        !open(x + 2, y + 1, BLOCK_NORTH_AND_SOUTH_WEST);
            }
            if (!open(x + size, y + size, BLOCK_NORTH_EAST)) {
                return true;
            }
            for (int mid = 1; mid < size; mid++) {
                if (!open(x + mid, y + size, BLOCK_SOUTH_EAST_AND_WEST) ||
                        !open(x + size, y + mid, BLOCK_NORTH_AND_SOUTH_WEST)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Not instantiable.
     */
    private StepValidator() {
    }
}
