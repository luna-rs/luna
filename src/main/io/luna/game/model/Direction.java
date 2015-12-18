package io.luna.game.model;

/**
 * An enumerated type whose elements represent a single movement direction.
 *
 * @author lare96 <http://github.org/lare96>
 */
public enum Direction {
    NONE(-1),
    NORTH_WEST(0),
    NORTH(1),
    NORTH_EAST(2),
    WEST(3),
    EAST(4),
    SOUTH_WEST(5),
    SOUTH(6),
    SOUTH_EAST(7);

    /**
     * The identifier for this {@code Direction}.
     */
    private final int id;

    /**
     * Creates a new {@link Direction}.
     *
     * @param id The identifier for this {@code Direction}.
     */
    private Direction(int id) {
        this.id = id;
    }

    /**
     * @return The identifier for this {@code Direction}.
     */
    public final int getId() {
        return id;
    }
}