package io.luna.game.model.area;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.Position;

import java.awt.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The polygonal implementation.
 */
public class PolygonArea extends Area {

    /**
     * The rectangular bounding box constraints of this polygon.
     */
    private int southWestX;
    private int southWestY;
    private int northEastX;
    private int northEastY;

    /**
     * The points of this polygon.
     */
    private int npoints;
    private int[] xpoints;
    private int[] ypoints;

    /**
     * The precomputed hashcode.
     */
    private final int hashCode;

    /**
     * The set of vertices that make up this polygon.
     */
    private final Set<Point> vertices;

    /**
     * Creates a new {@link PolygonArea}.
     *
     * @param verticesList The set of vertices that make up this polygon.
     */
    PolygonArea(List<Point> verticesList) {
        // Deep copy of vertices.
        vertices = new LinkedHashSet<>();
        for (Point point : verticesList) {
            vertices.add(point.getLocation());
        }

        // Precompute hashcode
        hashCode = Objects.hashCode(vertices);

        // Extract the points for this polygon from the vertices
        int index = 0;
        int totalSize = vertices.size();
        int[] x = new int[totalSize];
        int[] y = new int[totalSize];
        for (Point point : vertices) {
            x[index] = (int) point.getX();
            y[index] = (int) point.getY();
            index++;
        }

        this.npoints = totalSize;
        this.xpoints = x;
        this.ypoints = y;
        calculateBounds(xpoints, ypoints, npoints);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PolygonArea that = (PolygonArea) o;
        return Objects.equal(vertices, that.vertices);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean contains(Position position) {
        int x = position.getX();
        int y = position.getY();

        if (npoints <= 2 || !inBounds(x, y)) {
            return false;
        }
        int hits = 0;

        int lastx = xpoints[npoints - 1];
        int lasty = ypoints[npoints - 1];
        int curx, cury;

        // Walk the edges of the polygon
        for (int i = 0; i < npoints; lastx = curx, lasty = cury, i++) {
            curx = xpoints[i];
            cury = ypoints[i];

            if (cury == lasty) {
                continue;
            }

            int leftx;
            if (curx < lastx) {
                if (x >= lastx) {
                    continue;
                }
                leftx = curx;
            } else {
                if (x >= curx) {
                    continue;
                }
                leftx = lastx;
            }

            double test1, test2;
            if (cury < lasty) {
                if (y < cury || y >= lasty) {
                    continue;
                }
                if (x < leftx) {
                    hits++;
                    continue;
                }
                test1 = x - curx;
                test2 = y - cury;
            } else {
                if (y < lasty || y >= cury) {
                    continue;
                }
                if (x < leftx) {
                    hits++;
                    continue;
                }
                test1 = x - lastx;
                test2 = y - lasty;
            }

            if (test1 < (test2 / (lasty - cury) * (lastx - curx))) {
                hits++;
            }
        }

        return ((hits & 1) != 0);
    }

    @Override
    public int size() {
        return getPositionSet().size();
    }

    @Override
    public Position randomPosition() {
        ImmutableSet<Position> positionSet = getPositionSet();
        int counter = 0;
        int n = ThreadLocalRandom.current().nextInt(0, positionSet.size());
        for (Position position : positionSet) {
            if (counter++ == n) {
                return position;
            }
        }
        throw new IllegalStateException("unexpected");
    }

    @Override
    public ImmutableSet<Position> computePositionSet() {

        // Build it into a simple box area, get every position inside.
        ImmutableSet<Position> outerPositionSet =
                Area.of(southWestX, southWestY, northEastX, northEastY).getPositionSet();

        // Loop through the positions, save the ones contained within our actual polygon.
        ImmutableSet.Builder<Position> innerPositionSet = ImmutableSet.builder();
        for (Position outerPosition : outerPositionSet) {
            if (contains(outerPosition)) {
                innerPositionSet.add(outerPosition);
            }
        }
        return innerPositionSet.build();
    }

    private void calculateBounds(int[] xpoints, int[] ypoints, int npoints) {
        int boundsMinX = Integer.MAX_VALUE;
        int boundsMinY = Integer.MAX_VALUE;
        int boundsMaxX = Integer.MIN_VALUE;
        int boundsMaxY = Integer.MIN_VALUE;

        for (int i = 0; i < npoints; i++) {
            int x = xpoints[i];
            boundsMinX = Math.min(boundsMinX, x);
            boundsMaxX = Math.max(boundsMaxX, x);
            int y = ypoints[i];
            boundsMinY = Math.min(boundsMinY, y);
            boundsMaxY = Math.max(boundsMaxY, y);
        }
        this.southWestX = boundsMinX;
        this.southWestY = boundsMinY;
        this.northEastX = boundsMaxX;
        this.northEastY = boundsMaxY;
    }

    /**
     * Checks if a point is within the bounds. This is an early check to avoid checking all the points via the logic in contains.
     */
    private boolean inBounds(int x, int y) {
        return x >= this.southWestX && y >= this.southWestY && x < northEastX && y < northEastY;
    }
}
