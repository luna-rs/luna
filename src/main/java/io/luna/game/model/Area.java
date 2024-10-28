package io.luna.game.model;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import io.luna.util.RandomUtils;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A type of {@link Location} made up of vertices to form a polygon.
 *
 * @author lare96
 */
public abstract class Area implements Location {

    /**
     * Creates a simple box-like {@link Area} requiring only the south-west and  north-east
     * coordinates.
     *
     * @param southWestX The south-west x coordinate.
     * @param southWestY The south-west y coordinate.
     * @param northEastX The north-east x coordinate.
     * @param northEastY The north-east y coordinate.
     * @return The created {@link Area} instance.
     */
    public static Area of(int southWestX, int southWestY, int northEastX, int northEastY) {
        return new SimpleBoxArea(southWestX, southWestY, northEastX, northEastY);
    }

    /**
     * Creates an arbitrary polygonal {@link Area} requiring a list of vertices that make it up.
     *
     * @param vertices The vertices that make up this polygon.
     * @return The created {@link Area} instance.
     */
    public static Area of(List<Point> vertices) {
        return new PolygonArea(vertices);
    }

    /**
     * The simple box implementation.
     */
    private static class SimpleBoxArea extends Area {

        /**
         * The south-west x coordinate.
         */
        private final int southWestX;

        /**
         * The south-west y coordinate.
         */
        private final int southWestY;

        /**
         * The north-east x coordinate.
         */
        private final int northEastX;

        /**
         * The north-east y coordinate.
         */
        private final int northEastY;

        /**
         * Creates a new {@link SimpleBoxArea}.
         *
         * @param southWestX The south-west x coordinate.
         * @param southWestY The south-west y coordinate.
         * @param northEastX The north-east x coordinate.
         * @param northEastY The north-east y coordinate.
         */
        private SimpleBoxArea(int southWestX, int southWestY, int northEastX, int northEastY) {
            checkArgument(northEastX >= southWestX, "northEastX cannot be smaller than southWestX");
            checkArgument(northEastY >= southWestY, "northEastY cannot be smaller than southWestY");
            this.southWestX = southWestX;
            this.southWestY = southWestY;
            this.northEastX = northEastX;
            this.northEastY = northEastY;
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SimpleBoxArea that = (SimpleBoxArea) o;
            return southWestX == that.southWestX && southWestY == that.southWestY &&
                    northEastX == that.northEastX && northEastY == that.northEastY;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(southWestX, southWestY, northEastX, northEastY);
        }

        @Override
        public boolean contains(Position position) {
            return position.getX() >= southWestX &&
                    position.getX() <= northEastX &&
                    position.getY() >= southWestY &&
                    position.getY() <= northEastY;
        }

        @Override
        public int size() {
            return length() * width();
        }

        @Override
        public Position randomPosition() {
            int randomX = RandomUtils.exclusive(width()) + southWestX;
            int randomY = RandomUtils.exclusive(length()) + southWestY;
            return new Position(randomX, randomY);
        }

        @Override
        public ImmutableSet<Position> computePositionSet() {
           ImmutableSet.Builder<Position> set = ImmutableSet.builder();
            for (int x = southWestX; x <= northEastX; x++) {
                for (int y = southWestY; y <= northEastY; y++) {
                    set.add(new Position(x, y));
                }
            }
            return set.build();
        }

        /**
         * Returns the length of this area.
         */
        public int length() {
            // Areas are inclusive to base coordinates, so we add 1.
            return (northEastY - southWestY) + 1;
        }

        /**
         * Returns the width of this area.
         */
        public int width() {
            // Areas are inclusive to base coordinates, so we add 1.
            return (northEastX - southWestX) + 1;
        }
    }

    /**
     * The polygonal implementation.
     */
    private static class PolygonArea extends Area {

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
        private PolygonArea(List<Point> verticesList) {
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
            PolygonArea that = (PolygonArea) o;
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

    /**
     * An immutable cache of every position contained by this area.
     */
    private ImmutableSet<Position> positions;

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract boolean contains(Position position);

    /**
     * The total number of {@link Position} coordinates within this area.
     */
    public abstract int size();

    /**
     * Retrieves a random {@link Position} contained within this area.
     */
    public abstract Position randomPosition();

    /**
     * Computes every single possible {@link Position} contained within this area.
     */
    abstract ImmutableSet<Position> computePositionSet();

    /**
     * Retrieves every single possible {@link Position} contained within this area.
     */
    public final ImmutableSet<Position> getPositionSet() {
        if (positions == null) {
            positions = computePositionSet();
        }
        return positions;
    }
}