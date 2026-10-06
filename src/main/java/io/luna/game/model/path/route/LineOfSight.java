package io.luna.game.model.path.route;

import io.luna.game.model.collision.CollisionView;

import static io.luna.game.model.collision.CollisionFlag.*;

/**
 * Determines whether there is a clear line between two tiles, for sight and for walking.
 * <p>
 * The line is traced across the tiles between the two ends, starting from the closest points of the two mobs when
 * they are larger than a tile. Each tile that the line crosses is tested against the walls and objects on the side the
 * line passes through. The tracing uses fixed point arithmetic with 16 bits for the fraction, so the tiles it crosses
 * are the same ones the real game crosses.
 * <p>
 * The algorithm follows rsmod's routefinder.
 *
 * @author hydrozoa
 */
public final class LineOfSight {

    /**
     * The flags that stop sight from passing through the north side of a tile.
     */
    private static final int SIGHT_BLOCKED_NORTH = LOC_PROJ_BLOCKER | WALL_NORTH_PROJ_BLOCKER;

    /**
     * The flags that stop sight from passing through the east side of a tile.
     */
    private static final int SIGHT_BLOCKED_EAST = LOC_PROJ_BLOCKER | WALL_EAST_PROJ_BLOCKER;

    /**
     * The flags that stop sight from passing through the south side of a tile.
     */
    private static final int SIGHT_BLOCKED_SOUTH = LOC_PROJ_BLOCKER | WALL_SOUTH_PROJ_BLOCKER;

    /**
     * The flags that stop sight from passing through the west side of a tile.
     */
    private static final int SIGHT_BLOCKED_WEST = LOC_PROJ_BLOCKER | WALL_WEST_PROJ_BLOCKER;

    /**
     * The flags that stop walking through the north side of a tile.
     */
    private static final int WALK_BLOCKED_NORTH = WALL_NORTH | LOC | GROUND_DECOR | BLOCK_WALK;

    /**
     * The flags that stop walking through the east side of a tile.
     */
    private static final int WALK_BLOCKED_EAST = WALL_EAST | LOC | GROUND_DECOR | BLOCK_WALK;

    /**
     * The flags that stop walking through the south side of a tile.
     */
    private static final int WALK_BLOCKED_SOUTH = WALL_SOUTH | LOC | GROUND_DECOR | BLOCK_WALK;

    /**
     * The flags that stop walking through the west side of a tile.
     */
    private static final int WALK_BLOCKED_WEST = WALL_WEST | LOC | GROUND_DECOR | BLOCK_WALK;

    /**
     * The number of bits of the fraction of a coordinate.
     */
    private static final int SCALE = 16;

    /**
     * Half of a tile, in the fixed point scale.
     */
    private static final int HALF_TILE = (1 << SCALE) / 2;

    /**
     * Determines if there is a clear line of sight between two tiles.
     *
     * @param view The collision flags to read.
     * @param level The height level.
     * @param srcX The absolute x coordinate of the south west tile of the viewer.
     * @param srcY The absolute y coordinate of the south west tile of the viewer.
     * @param destX The absolute x coordinate of the south west tile of what is viewed.
     * @param destY The absolute y coordinate of the south west tile of what is viewed.
     * @param srcWidth The width of the viewer.
     * @param srcLength The length of the viewer.
     * @param destWidth The width of what is viewed.
     * @param destLength The length of what is viewed.
     * @param extraFlag Flags that also stop sight.
     * @return {@code true} if the line is clear.
     */
    public static boolean hasLineOfSight(CollisionView view, int level, int srcX, int srcY, int destX, int destY,
                                         int srcWidth, int srcLength, int destWidth, int destLength, int extraFlag) {
        return trace(view, level, srcX, srcY, destX, destY, srcWidth, srcLength, destWidth, destLength,
                SIGHT_BLOCKED_WEST | extraFlag, SIGHT_BLOCKED_EAST | extraFlag, SIGHT_BLOCKED_SOUTH | extraFlag,
                SIGHT_BLOCKED_NORTH | extraFlag, LOC | extraFlag, LOC_PROJ_BLOCKER | extraFlag, true);
    }

    /**
     * Determines if there is a clear line of sight between two single tiles.
     *
     * @param view The collision flags to read.
     * @param level The height level.
     * @param srcX The absolute x coordinate of the viewer.
     * @param srcY The absolute y coordinate of the viewer.
     * @param destX The absolute x coordinate of what is viewed.
     * @param destY The absolute y coordinate of what is viewed.
     * @return {@code true} if the line is clear.
     */
    public static boolean hasLineOfSight(CollisionView view, int level, int srcX, int srcY, int destX, int destY) {
        return hasLineOfSight(view, level, srcX, srcY, destX, destY, 1, 1, 1, 1, 0);
    }

    /**
     * Determines if there is a clear line of walk between two tiles, which is a line that nothing that stops walking
     * crosses.
     *
     * @param view The collision flags to read.
     * @param level The height level.
     * @param srcX The absolute x coordinate of the south west tile of the walker.
     * @param srcY The absolute y coordinate of the south west tile of the walker.
     * @param destX The absolute x coordinate of the south west tile of the destination.
     * @param destY The absolute y coordinate of the south west tile of the destination.
     * @param srcWidth The width of the walker.
     * @param srcLength The length of the walker.
     * @param destWidth The width of the destination.
     * @param destLength The length of the destination.
     * @param extraFlag Flags that also stop walking.
     * @return {@code true} if the line is clear.
     */
    public static boolean hasLineOfWalk(CollisionView view, int level, int srcX, int srcY, int destX, int destY,
                                        int srcWidth, int srcLength, int destWidth, int destLength, int extraFlag) {
        return trace(view, level, srcX, srcY, destX, destY, srcWidth, srcLength, destWidth, destLength,
                WALK_BLOCKED_WEST | extraFlag, WALK_BLOCKED_EAST | extraFlag, WALK_BLOCKED_SOUTH | extraFlag,
                WALK_BLOCKED_NORTH | extraFlag, LOC | extraFlag, LOC_PROJ_BLOCKER | extraFlag, false);
    }

    /**
     * Traces the line between two tiles.
     *
     * @param los {@code true} to trace sight, which is stopped by what stops projectiles, or {@code false} to trace
     * walking.
     */
    private static boolean trace(CollisionView view, int level, int srcX, int srcY, int destX, int destY,
                                 int srcWidth, int srcLength, int destWidth, int destLength, int flagWest,
                                 int flagEast, int flagSouth, int flagNorth, int flagLocation,
                                 int flagProjectileBlocker, boolean los) {
        int startX = coordinate(srcX, destX, srcWidth);
        int startY = coordinate(srcY, destY, srcLength);
        int endX = coordinate(destX, srcX, destWidth);
        int endY = coordinate(destY, srcY, destLength);
        if (startX == endX && startY == endY) {
            return true;
        }
        if (los && flagged(view, startX, startY, level, flagLocation)) {
            return false;
        }

        int deltaX = endX - startX;
        int deltaY = endY - startY;
        boolean travelEast = deltaX >= 0;
        boolean travelNorth = deltaY >= 0;
        int xFlags = travelEast ? flagWest : flagEast;
        int yFlags = travelNorth ? flagSouth : flagNorth;

        if (Math.abs(deltaX) > Math.abs(deltaY)) {
            int offsetX = travelEast ? 1 : -1;
            int offsetY = travelNorth ? 0 : -1;
            int scaledY = scaleUp(startY) + HALF_TILE + offsetY;
            int tangent = scaleUp(deltaY) / Math.abs(deltaX);
            int currentX = startX;
            while (currentX != endX) {
                currentX += offsetX;
                int currentY = scaleDown(scaledY);
                if (los && currentX == endX && currentY == endY) {
                    xFlags &= ~flagProjectileBlocker;
                }
                if (flagged(view, currentX, currentY, level, xFlags)) {
                    return false;
                }
                scaledY += tangent;
                int nextY = scaleDown(scaledY);
                if (los && currentX == endX && nextY == endY) {
                    yFlags &= ~flagProjectileBlocker;
                }
                if (nextY != currentY && flagged(view, currentX, nextY, level, yFlags)) {
                    return false;
                }
            }
        } else {
            int offsetX = travelEast ? 0 : -1;
            int offsetY = travelNorth ? 1 : -1;
            int scaledX = scaleUp(startX) + HALF_TILE + offsetX;
            int tangent = scaleUp(deltaX) / Math.abs(deltaY);
            int currentY = startY;
            while (currentY != endY) {
                currentY += offsetY;
                int currentX = scaleDown(scaledX);
                if (los && currentX == endX && currentY == endY) {
                    yFlags &= ~flagProjectileBlocker;
                }
                if (flagged(view, currentX, currentY, level, yFlags)) {
                    return false;
                }
                scaledX += tangent;
                int nextX = scaleDown(scaledX);
                if (los && nextX == endX && currentY == endY) {
                    xFlags &= ~flagProjectileBlocker;
                }
                if (nextX != currentX && flagged(view, nextX, currentY, level, xFlags)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Returns the coordinate of the point of a mob that the line starts from: the coordinate of {@code a} that is
     * closest to {@code b}, out of the {@code size} tiles that {@code a} covers.
     */
    private static int coordinate(int a, int b, int size) {
        if (a >= b) {
            return a;
        } else if (a + size - 1 <= b) {
            return a + size - 1;
        }
        return b;
    }

    /**
     * Returns whether any of the flags are set on a tile.
     */
    private static boolean flagged(CollisionView view, int x, int y, int level, int flags) {
        return (view.get(x, y, level) & flags) != 0;
    }

    /**
     * Converts tiles to the fixed point scale.
     */
    private static int scaleUp(int tiles) {
        return tiles << SCALE;
    }

    /**
     * Converts the fixed point scale to whole tiles.
     */
    private static int scaleDown(int scaled) {
        return scaled >>> SCALE;
    }

    /**
     * Not instantiable.
     */
    private LineOfSight() {
    }
}
