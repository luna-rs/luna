package io.luna.game.model.region;

import io.luna.game.model.Position;

import java.util.Objects;

/**
 * A model representing absolute coordinates divided by {@code 32}.
 *
 * @author Graham
 * @author lare96 <http://github.org/lare96>
 */
public final class RegionCoordinates {

    /**
     * Creates a new {@link RegionCoordinates} from the argued position.
     */
    public static RegionCoordinates create(Position pos) {
        return new RegionCoordinates(pos.getX() / 32, pos.getY() / 32);
    }

    /**
     * The x coordinate.
     */
    private final int x;

    /**
     * The y coordinate.
     */
    private final int y;

    /**
     * Creates a new {@link RegionCoordinates}.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    RegionCoordinates(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof RegionCoordinates) {
            RegionCoordinates other = (RegionCoordinates) obj;
            return other.x == x && other.y == y;
        }
        return false;
    }

    /**
     * @return The x coordinate.
     */
    public int getX() {
        return x;
    }

    /**
     * @return The y coordinate.
     */
    public int getY() {
        return y;
    }
}
