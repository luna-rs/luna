package io.luna.game.model.area;

import com.google.common.collect.ImmutableSet;
import io.luna.game.model.Location;
import io.luna.game.model.Position;

import java.awt.*;
import java.util.List;

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
    public static SimpleBoxArea of(int southWestX, int southWestY, int northEastX, int northEastY) {
        return new SimpleBoxArea(southWestX, southWestY, northEastX, northEastY);
    }

    /**
     * Creates an arbitrary polygonal {@link Area} requiring a list of vertices.
     *
     * @param vertices The vertices that make up this polygon.
     * @return The created {@link Area} instance.
     */
    public static PolygonArea of(List<Point> vertices) {
        return new PolygonArea(vertices);
    }

    /**
     * Creates a {@link CircularArea} instance.
     *
     * @param center The center of the circle
     * @param radius The distance from the center of the circle
     * @return A circular area
     */
    public static CircularArea of(Point center, int radius) {
        return new CircularArea(center.x, center.y, radius);
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