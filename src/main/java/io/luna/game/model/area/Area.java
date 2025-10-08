package io.luna.game.model.area;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
import io.luna.util.RandomUtils;

import java.awt.*;
import java.util.List;

/**
 * A type of {@link Locatable} made up of vertices to form a polygon.
 *
 * @author lare96
 */
public abstract class Area implements Locatable {

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
    private ImmutableList<Position> positions;

    /**
     * The anchor position used in {@link #location()}.
     */
    private Position anchorPosition;

    @Override
    public final Position location() {
        if (anchorPosition == null) {
            anchorPosition = randomPosition();
        }
        return anchorPosition;
    }

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);

    /**
     * The total number of {@link Position} coordinates within this area.
     */
    public abstract int size();

    /**
     * Retrieves a random {@link Position} contained within this area.
     */
    public Position randomPosition() {
        return RandomUtils.random(computePositions());
    }

    /**
     * Computes every single possible {@link Position} contained within this area.
     */
    abstract ImmutableList<Position> computePositions();

    /**
     * Retrieves every single possible {@link Position} contained within this area.
     */
    public final ImmutableList<Position> getPositions() {
        if (positions == null) {
            positions = computePositions();
        }
        return positions;
    }

    /**
     * Sets the anchor position of this area. Must be a valid position within this area.
     *
     * @param position The anchor location.
     */
    public void setAnchorPosition(Position position) {
        if (contains(position)) {
            anchorPosition = position;
        }
    }
}