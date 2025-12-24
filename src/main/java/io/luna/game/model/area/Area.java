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
        int normalizedSwX = Math.min(southWestX, northEastX);
        int normalizedSwY = Math.min(southWestY, northEastY);
        int normalizedNeX = Math.max(southWestX, northEastX);
        int normalizedNeY = Math.max(southWestY, northEastY);

        SimpleBoxArea box = new SimpleBoxArea(normalizedSwX, normalizedSwY, normalizedNeX, normalizedNeY);
        box.setAnchorPosition(new Position(normalizedSwX, normalizedSwY));
        return box;
    }

    /**
     * Creates a new {@link SimpleBoxArea} centered around a position with a specified radius.
     *
     * @param center The center {@link Position}.
     * @param radius The radius (number of tiles) extending outward in all directions.
     */
    public static SimpleBoxArea of(Position center, int radius) {
        SimpleBoxArea box = of(center.getX() - radius, center.getY() - radius,
                center.getX() + radius, center.getY() + radius);
        box.setAnchorPosition(center);
        return box;
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
        CircularArea area = new CircularArea(center.x, center.y, radius);
        area.setAnchorPosition(new Position(center.x, center.y));
        return area;
    }

    /**
     * An immutable cache of every position contained by this area.
     */
    private ImmutableList<Position> positions;

    /**
     * The anchor position used in {@link #absLocation()}.
     */
    private Position anchorPosition;

    @Override
    public final Position absLocation() {
        return getAnchorPosition();
    }

    @Override
    public int getX() {
        return getAnchorPosition().getX();
    }

    @Override
    public int getY() {
        return getAnchorPosition().getY();
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
     * @return The anchor position used in {@link #absLocation()}. Generates a new one using {@link #randomPosition()}
     * if needed.
     */
    public Position getAnchorPosition() {
        if (anchorPosition == null) {
            anchorPosition = randomPosition();
        }
        return anchorPosition;
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