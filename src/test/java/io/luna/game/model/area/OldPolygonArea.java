package io.luna.game.model.area;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.Position;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The polygonal implementation.
 */
public class OldPolygonArea extends Area {

    /**
     * The representation of our arbitrary polygon.
     */
    private final Polygon polygon;

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
    OldPolygonArea(List<Point> verticesList) {
        // Deep copy of vertices.
        vertices = new LinkedHashSet<>();
        for (Point point : verticesList) {
            vertices.add(point.getLocation());
        }

        // Precompute hashcode
        hashCode = Objects.hashCode(vertices);

        // Reformat to create java.awt.Polygon
        int index = 0;
        int totalSize = vertices.size();
        int[] x = new int[totalSize];
        int[] y = new int[totalSize];
        for (Point point : vertices) {
            x[index] = (int) point.getX();
            y[index] = (int) point.getY();
            index++;
        }
        polygon = new Polygon(x, y, totalSize);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OldPolygonArea that = (OldPolygonArea) o;
        return Objects.equal(vertices, that.vertices);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean contains(Position position) {
        return polygon.contains(position.getX(), position.getY());
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
        // Create a rectangle that encompasses our polygon.
        Rectangle2D outer = polygon.getBounds2D();
        int southWestX = (int) outer.getMinX();
        int southWestY = (int) outer.getMinY();
        int northEastX = (int) outer.getMaxX();
        int northEastY = (int) outer.getMaxY();

        // Build it into a simple box area, get every position inside.
        ImmutableSet<Position> outerPositionSet =
                Area.of(southWestX, southWestY, northEastX, northEastY).getPositionSet();

        // Loop through the positions, save the ones contained within our actual polygon.
        ImmutableSet.Builder<Position> innerPositionSet = ImmutableSet.builder();
        for (Position outerPosition : outerPositionSet) {
            int outerPositionX = outerPosition.getX();
            int outerPositionY = outerPosition.getY();
            if (polygon.contains(outerPositionX, outerPositionY)) {
                innerPositionSet.add(outerPosition);
            }
        }
        return innerPositionSet.build();
    }
}
