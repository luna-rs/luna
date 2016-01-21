package io.luna.game.model.region;

import io.luna.game.model.Position;

import java.util.Objects;

/**
 * An {@code x} and {@code y} coordinate equal to a {@link Position}s coordinates divided by {@code 32}.
 *
 * @author Graham
 * @author lare96 <http://github.org/lare96>
 */
public final class RegionCoordinates {

    /**
     * Creates a new {@link RegionCoordinates} from {@code pos}.
     *
     * @param pos The position to create from.
     * @return The newly created {@code RegionCoordinates}.
     */
    public static RegionCoordinates create(Position pos) {
        return new RegionCoordinates(pos.getX() / 32, pos.getY() / 32);
    }

    /**
     * The {@code X} region coordinate.
     */
    private final int x;

    /**
     * The {@code Y} region coordinate.
     */
    private final int y;

    /**
     * Creates a new {@link RegionCoordinates}.
     *
     * @param x The {@code X} region coordinate.
     * @param y The {@code Y} region coordinate.
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
     * @return The {@code X} region coordinate.
     */
    public int getX() {
        return x;
    }

    /**
     * @return The {@code Y} region coordinate.
     */
    public int getY() {
        return y;
    }
}
