package io.luna.game.model.object;

/**
 * An enumerated type whose elements represent different object directions.
 *
 * @author lare96 <http://github.com/lare96>
 */
public enum ObjectDirection {
    NORTH(1),
    SOUTH(3),
    EAST(2),
    WEST(0);

    /**
     * The direction identifier.
     */
    private final int id;

    /**
     * Creates a new {@link ObjectDirection}.
     *
     * @param id The direction identifier.
     */
    ObjectDirection(int id) {
        this.id = id;
    }

    /**
     * @return The direction identifier.
     */
    public final int getId() {
        return id;
    }

}