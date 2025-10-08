package io.luna.game.model.area;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import io.luna.game.model.Position;

/**
 * A model representing a circular {@link Area} within the game world. The area is defined by a
 * {@link Position center} point and an integer {@link #radius}.
 *
 * @author notjuanortiz
 */
public final class CircularArea extends Area {

    /**
     * The center position of this circular area.
     */
    private final Position center;

    /**
     * The radius of this circular area. Must be at least 1.
     */
    private final int radius;

    /**
     * Creates a new {@code CircularArea} instance with the specified center coordinates and radius.
     *
     * @param centerX The X-coordinate of the center.
     * @param centerY The Y-coordinate of the center.
     * @param radius The radius of the circle. Must be > 1.
     * @throws IllegalArgumentException If {@code radius} is less than 1.
     */
    public CircularArea(int centerX, int centerY, int radius) {
        if (radius < 1) {
            throw new IllegalArgumentException("Circular radius cannot be less than 1");
        }
        this.radius = radius;
        center = new Position(centerX, centerY);
        setAnchorPosition(center);

    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CircularArea)) return false;
        CircularArea that = (CircularArea) o;
        return radius == that.radius && Objects.equal(center, that.center);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(center, radius);
    }

    @Override
    public boolean contains(Position position) {
        return center.isWithinDistance(position, radius);
    }

    @Override
    public int size() {
        return (int) (Math.PI * radius * radius);
    }

    @Override
    ImmutableList<Position> computePositions() {
        ImmutableList.Builder<Position> set = ImmutableList.builder();
        int radiusSquared = radius * radius;

        // Pythagorean theorem works for circles too
        for (int x = center.getX() - radius; x < center.getX() + radius; x++) {
            for (int y = center.getY() - radius; y < center.getY() + radius; y++) {
                int dx = x - center.getX();
                int dy = y - center.getY();
                if (dx * dx + dy * dy <= radiusSquared) {
                    set.add(new Position(x, y));
                }
            }
        }
        return set.build();
    }
}
