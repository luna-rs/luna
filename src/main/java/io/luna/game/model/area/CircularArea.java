package io.luna.game.model.area;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import io.luna.game.model.Position;

/**
 * A circular {@link Area} defined by a center {@link Position} and a tile radius.
 * <p>
 * {@link #contains(Position)} uses {@link Position#isWithinDistance(Position, int)} to determine containment.
 * <p>
 * The position enumeration in {@link #computePositions()} iterates over the circle's bounding square and includes
 * only points that satisfy {@code dx² + dy² <= radius²}.
 *
 * @author notjuanortiz
 */
public final class CircularArea extends Area {

    /**
     * The center position of this area.
     */
    private final Position center;

    /**
     * The radius in tiles. Must be at least 1.
     */
    private final int radius;

    /**
     * Creates a new {@link CircularArea}.
     *
     * @param centerX The center x coordinate.
     * @param centerY The center y coordinate.
     * @param radius The radius in tiles (must be {@code >= 1}).
     * @throws IllegalArgumentException if {@code radius < 1}.
     */
    public CircularArea(int centerX, int centerY, int radius) {
        if (radius < 1) {
            throw new IllegalArgumentException("Circular radius cannot be less than 1");
        }
        this.radius = radius;
        this.center = new Position(centerX, centerY);

        // Anchor defaults to the center for circles.
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

    /**
     * Checks whether {@code position} lies within {@link #radius} tiles of {@link #center}.
     *
     * @param position The position to test.
     * @return {@code true} if within radius.
     */
    @Override
    public boolean contains(Position position) {
        if(!center.isWithinDistance(position, radius)) {
            return false;
        }
        return computePositions().contains(position);
    }

    /**
     * Returns the approximate area of the circle in tiles.
     * <p>
     * This is a mathematical estimate ({@code πr²}), not necessarily equal to {@link #getPositions()} size.
     *
     * @return Approximate number of tiles.
     */
    @Override
    public int size() {
        return (int) (Math.PI * radius * radius);
    }

    /**
     * Enumerates every tile position contained within this circle.
     *
     * @return An immutable list of contained positions.
     */
    @Override
    ImmutableList<Position> computePositions() {
        ImmutableList.Builder<Position> set = ImmutableList.builder();
        int radiusSquared = radius * radius;

        int cx = center.getX();
        int cy = center.getY();

        // Iterate bounding square and keep points within the circle.
        for (int x = cx - radius; x < cx + radius; x++) {
            for (int y = cy - radius; y < cy + radius; y++) {
                int dx = x - cx;
                int dy = y - cy;
                if (dx * dx + dy * dy <= radiusSquared) {
                    set.add(new Position(x, y));
                }
            }
        }
        return set.build();
    }
}
