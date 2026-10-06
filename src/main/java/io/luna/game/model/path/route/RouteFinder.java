package io.luna.game.model.path.route;

import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Finds routes between two tiles with a breadth first search, the way the real game does.
 * <p>
 * The search spreads out from the start one tile at a time, trying west, east, south, north and then the four
 * diagonals, which makes routes come out with the same shape as the ones in the real game. It looks at a square of
 * {@value #SEARCH_SIZE} tiles around the start, so it only finds routes of up to {@code SEARCH_SIZE / 2} tiles in each
 * direction. When the destination can't be reached, the tile closest to it among the ones the search got to can be
 * used instead.
 * <p>
 * A finder reuses its buffers between searches, so a finder is not thread safe. Give every thread its own.
 * <p>
 * The algorithm follows rsmod's routefinder.
 * <p>
 * Portions of this class are derived from rsmod's routefinder, which is licensed under the ISC License, Copyright (c)
 * 2025 RS Mod. See the third party notices in LICENSE.txt.
 *
 * @author hydrozoa
 */
public final class RouteFinder {

    /**
     * The width and length of the square of tiles that is searched.
     */
    public static final int SEARCH_SIZE = 128;

    /**
     * The size of the queue of tiles that are waiting to be expanded. Must be a power of two.
     */
    private static final int QUEUE_SIZE = 4096;

    /**
     * The distance of a tile that the search has not reached.
     */
    private static final int UNREACHED = 99_999_999;

    /**
     * The direction stored for the start tile, which has no tile to come from.
     */
    private static final int START_DIRECTION = 99;

    /**
     * The cost to beat when looking for the closest tile to an unreachable destination.
     */
    private static final int MAX_ALTERNATIVE_COST = 1000;

    /**
     * The longest route, in steps, that a closest tile may be at the end of.
     */
    private static final int MAX_ALTERNATIVE_STEPS = 100;

    /**
     * How far from the destination a closest tile can be, in tiles along each axis.
     */
    private static final int MAX_ALTERNATIVE_RANGE = 10;

    /*
     * The direction a tile was reached from, as the side of that tile the previous tile is on.
     */
    private static final int NORTH = 0x1;
    private static final int EAST = 0x2;
    private static final int SOUTH = 0x4;
    private static final int WEST = 0x8;

    /**
     * The x offset of each direction a step can be taken in, in the order west, east, south, north, south west,
     * south east, north west and north east.
     */
    private static final int[] STEP_X = {-1, 1, 0, 0, -1, 1, -1, 1};

    /**
     * The y offset of each direction a step can be taken in, in the same order as {@link #STEP_X}.
     */
    private static final int[] STEP_Y = {0, 0, -1, 1, -1, -1, 1, 1};

    /**
     * The side of the tile that was stepped onto which the tile it was stepped from is on, for each direction a step
     * can be taken in, in the same order as {@link #STEP_X}.
     */
    private static final int[] CAME_FROM = {EAST, WEST, NORTH, SOUTH, NORTH | EAST, NORTH | WEST, SOUTH | EAST,
            SOUTH | WEST};

    /**
     * The direction each tile was reached from, or {@code 0} if the tile has not been reached.
     */
    private final int[] directions = new int[SEARCH_SIZE * SEARCH_SIZE];

    /**
     * The number of steps it took to reach each tile.
     */
    private final int[] distances = new int[SEARCH_SIZE * SEARCH_SIZE];

    /**
     * The x coordinates of the tiles waiting to be expanded.
     */
    private final int[] queueX = new int[QUEUE_SIZE];

    /**
     * The y coordinates of the tiles waiting to be expanded.
     */
    private final int[] queueY = new int[QUEUE_SIZE];

    /**
     * The index of the next tile to expand.
     */
    private int reader;

    /**
     * The index the next tile is added at.
     */
    private int writer;

    /**
     * The x coordinate of the tile being expanded, and of the end of the route once it is found.
     */
    private int currentX;

    /**
     * The y coordinate of the tile being expanded, and of the end of the route once it is found.
     */
    private int currentY;

    /**
     * Searches for a route from one tile to another.
     *
     * @param view The collision flags to search through.
     * @param level The height level of both tiles.
     * @param startX The absolute x coordinate of the south west tile the mob covers.
     * @param startY The absolute y coordinate of the south west tile the mob covers.
     * @param destX The absolute x coordinate of the destination.
     * @param destY The absolute y coordinate of the destination.
     * @param size The width and length of the mob, in tiles.
     * @param extraFlag Flags that also stop a step, such as {@code BLOCK_NPCS}.
     * @param strategy The rule for moving onto a tile.
     * @param moveNear {@code true} to settle for the closest tile that can be reached when the destination can't be.
     * @param maxWaypoints The most waypoints to return. The ones at the end of a longer route are dropped.
     * @return The route, which has no waypoints if the mob is already at the destination.
     */
    public Route find(CollisionView view, int level, int startX, int startY, int destX, int destY, int size,
                      int extraFlag, RouteStrategy strategy, boolean moveNear, int maxWaypoints) {
        return find(view, level, startX, startY, destX, destY, size, extraFlag, strategy, moveNear, maxWaypoints,
                RouteStyle.STANDARD);
    }

    /**
     * Searches for a route from one tile to another, with a {@link RouteStyle} that decides which of the routes of the
     * shortest length is picked.
     *
     * @param view The collision flags to search through.
     * @param level The height level of both tiles.
     * @param startX The absolute x coordinate of the south west tile the mob covers.
     * @param startY The absolute y coordinate of the south west tile the mob covers.
     * @param destX The absolute x coordinate of the destination.
     * @param destY The absolute y coordinate of the destination.
     * @param size The width and length of the mob, in tiles.
     * @param extraFlag Flags that also stop a step, such as {@code BLOCK_NPCS}.
     * @param strategy The rule for moving onto a tile.
     * @param moveNear {@code true} to settle for the closest tile that can be reached when the destination can't be.
     * @param maxWaypoints The most waypoints to return. The ones at the end of a longer route are dropped.
     * @param style The shape of the route.
     * @return The route, which has no waypoints if the mob is already at the destination.
     */
    public Route find(CollisionView view, int level, int startX, int startY, int destX, int destY, int size,
                      int extraFlag, RouteStrategy strategy, boolean moveNear, int maxWaypoints, RouteStyle style) {
        reset();
        int baseX = startX - SEARCH_SIZE / 2;
        int baseY = startY - SEARCH_SIZE / 2;
        int localStartX = startX - baseX;
        int localStartY = startY - baseY;
        int localDestX = destX - baseX;
        int localDestY = destY - baseY;
        add(localStartX, localStartY, START_DIRECTION, 0);

        boolean found = search(view, level, baseX, baseY, localDestX, localDestY, size, extraFlag, strategy,
                style.order());
        if (!found) {
            if (!moveNear || !findClosestTile(localDestX, localDestY)) {
                return Route.FAILED;
            }
        }

        Deque<Position> waypoints = new ArrayDeque<>();
        int nextDirection = directions[index(currentX, currentY)];
        int direction = -1;
        for (int i = 0; i < directions.length; i++) {
            if (currentX == localStartX && currentY == localStartY) {
                break;
            }
            if (direction != nextDirection) {
                direction = nextDirection;
                if (waypoints.size() >= maxWaypoints) {
                    waypoints.removeLast();
                }
                waypoints.addFirst(new Position(baseX + currentX, baseY + currentY, level));
            }
            if ((direction & EAST) != 0) {
                currentX++;
            } else if ((direction & WEST) != 0) {
                currentX--;
            }
            if ((direction & NORTH) != 0) {
                currentY++;
            } else if ((direction & SOUTH) != 0) {
                currentY--;
            }
            nextDirection = directions[index(currentX, currentY)];
        }
        List<Position> route = new ArrayList<>(waypoints);
        return new Route(route, !found, true);
    }

    /**
     * Spreads out from the start tile until the destination is reached or there is nowhere left to go.
     *
     * @param order The order to try the directions out of a tile in, as indices of {@link #STEP_X}.
     * @return {@code true} if the destination was reached, in which case {@link #currentX} and {@link #currentY} are
     * the destination.
     */
    private boolean search(CollisionView view, int level, int baseX, int baseY, int destX, int destY, int size,
                           int extraFlag, RouteStrategy strategy, int[] order) {
        // A large mob must fit inside the search area, so it stops short of the far edges by its size.
        int last = SEARCH_SIZE - size;
        while (writer != reader) {
            currentX = queueX[reader];
            currentY = queueY[reader];
            reader = (reader + 1) & (QUEUE_SIZE - 1);

            if (currentX == destX && currentY == destY) {
                return true;
            }

            int x = currentX;
            int y = currentY;
            int next = distances[index(x, y)] + 1;
            int absX = baseX + x;
            int absY = baseY + y;

            for (int direction : order) {
                int stepX = STEP_X[direction];
                int stepY = STEP_Y[direction];
                int nextX = x + stepX;
                int nextY = y + stepY;
                if (nextX >= 0 && nextX <= last && nextY >= 0 && nextY <= last && unreached(nextX, nextY) &&
                        StepValidator.canTravel(view, level, absX, absY, stepX, stepY, size, extraFlag, strategy)) {
                    add(nextX, nextY, CAME_FROM[direction], next);
                }
            }
        }
        return false;
    }

    /**
     * Finds the tile that the search reached which is closest to the destination, within a few tiles of it. Of tiles
     * that are equally close, the one with the shortest route wins. When one is found it becomes the end of the route.
     *
     * @return {@code true} if a tile was found.
     */
    private boolean findClosestTile(int destX, int destY) {
        int lowestCost = MAX_ALTERNATIVE_COST;
        int fewestSteps = MAX_ALTERNATIVE_STEPS;
        for (int x = destX - MAX_ALTERNATIVE_RANGE; x <= destX + MAX_ALTERNATIVE_RANGE; x++) {
            for (int y = destY - MAX_ALTERNATIVE_RANGE; y <= destY + MAX_ALTERNATIVE_RANGE; y++) {
                if (x < 0 || x >= SEARCH_SIZE || y < 0 || y >= SEARCH_SIZE ||
                        distances[index(x, y)] >= MAX_ALTERNATIVE_STEPS) {
                    continue;
                }
                int dx = x - destX;
                int dy = y - destY;
                int cost = dx * dx + dy * dy;
                if (cost < lowestCost || (cost == lowestCost && fewestSteps > distances[index(x, y)])) {
                    currentX = x;
                    currentY = y;
                    lowestCost = cost;
                    fewestSteps = distances[index(x, y)];
                }
            }
        }
        return lowestCost != MAX_ALTERNATIVE_COST;
    }

    /**
     * Returns whether the search has yet to reach a tile.
     */
    private boolean unreached(int x, int y) {
        return directions[index(x, y)] == 0;
    }

    /**
     * Records that a tile was reached, and queues it to be expanded.
     */
    private void add(int x, int y, int direction, int distance) {
        int index = index(x, y);
        directions[index] = direction;
        distances[index] = distance;
        queueX[writer] = x;
        queueY[writer] = y;
        writer = (writer + 1) & (QUEUE_SIZE - 1);
    }

    /**
     * Clears the results of the last search.
     */
    private void reset() {
        Arrays.fill(directions, 0);
        Arrays.fill(distances, UNREACHED);
        reader = 0;
        writer = 0;
    }

    /**
     * Returns the index of a tile of the search area in {@link #directions} and {@link #distances}.
     */
    private static int index(int x, int y) {
        return x * SEARCH_SIZE + y;
    }
}
