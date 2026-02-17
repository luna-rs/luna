package io.luna.game.model.area;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import io.luna.game.model.Position;

import java.awt.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A polygonal {@link Area} defined by an ordered set of vertices.
 * <p>
 * This implementation uses a ray-casting / crossing-number style algorithm in {@link #contains(Position)} and
 * accelerates checks with a precomputed rectangular bounding box.
 * <p>
 * Vertices are copied on construction to prevent external mutation. A precomputed {@link #hashCode} is stored
 * since the vertex set is immutable after construction.
 *
 * @author lare96
 */
public class PolygonArea extends Area {

    /**
     * Bounding box constraints (computed from vertices).
     */
    private int southWestX;
    private int southWestY;
    private int northEastX;
    private int northEastY;

    /**
     * The number of points in this polygon.
     */
    private int npoints;

    /**
     * The x coordinates of polygon vertices.
     */
    private int[] xpoints;

    /**
     * The y coordinates of polygon vertices.
     */
    private int[] ypoints;

    /**
     * Precomputed hash code (based on {@link #vertices}).
     */
    private final int hashCode;

    /**
     * The vertex set. Uses insertion order to preserve a stable polygon outline based on the input list.
     */
    private final Set<Point> vertices;

    /**
     * Creates a new {@link PolygonArea}.
     * <p>
     * The input list is deep-copied into {@link #vertices} and then converted into primitive coordinate arrays.
     *
     * @param verticesList The polygon vertices (in intended edge order).
     */
    PolygonArea(List<Point> verticesList) {

        // Deep copy of vertices.
        vertices = new LinkedHashSet<>();
        for (Point point : verticesList) {
            vertices.add(point.getLocation());
        }

        // Precompute hashcode.
        hashCode = Objects.hashCode(vertices);

        // Extract primitive coordinate arrays.
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PolygonArea that = (PolygonArea) o;
        return Objects.equal(vertices, that.vertices);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    /**
     * Determines whether {@code position} is contained within this polygon.
     * <p>
     * Returns {@code false} for degenerate polygons ({@code npoints <= 2}) and for any point outside the bounding box.
     *
     * @param position The position to test.
     * @return {@code true} if contained according to the ray-casting test.
     */
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

        // Walk the edges of the polygon.
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

    /**
     * Returns the number of cached positions in this area.
     * <p>
     * This delegates to {@link Area#getPositions()} which may allocate/cache the position list.
     */
    @Override
    public int size() {
        return getPositions().size();
    }

    /**
     * Enumerates all tile positions inside the polygon.
     * <p>
     * This implementation:
     * <ol>
     *     <li>Enumerates all tiles inside the polygon's bounding box.</li>
     *     <li>Filters them through {@link #contains(Position)}.</li>
     * </ol>
     *
     * @return An immutable list of all contained tile positions.
     */
    @Override
    public ImmutableList<Position> computePositions() {

        // Enumerate every position inside the bounding rectangle.
        ImmutableList<Position> outerPositions =
                Area.of(southWestX, southWestY, northEastX, northEastY).getPositions();

        // Filter down to only those contained by the polygon.
        ImmutableList.Builder<Position> innerPositions = ImmutableList.builder();
        for (Position outerPosition : outerPositions) {
            if (contains(outerPosition)) {
                innerPositions.add(outerPosition);
            }
        }
        return innerPositions.build();
    }

    /**
     * Computes the bounding rectangle for this polygon from its vertex arrays.
     */
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
     * Fast bounding-box early-out for containment checks.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @return {@code true} if within the polygon's bounding rectangle.
     */
    private boolean inBounds(int x, int y) {
        return x >= southWestX &&
                y >= southWestY &&
                x <= northEastX &&
                y <= northEastY;
    }
}
