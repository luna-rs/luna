package io.luna.game.model.mob.varp;

/**
 * A model representing a varP which stands for VarPlayer. They hold player related client values that can be changed
 * by the server.
 *
 * @author lare96
 */
public class Varp {

    /**
     * The id.
     */
    private final int id;

    /**
     * The value.
     */
    private final int value;

    /**
     * Creates a new {@link Varp}.
     *
     * @param id The id.
     * @param value The value.
     */
    public Varp(int id, int value) {
        this.id = id;
        this.value = value;
    }

    /**
     * @return The id.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The value.
     */
    public int getValue() {
        return value;
    }
}
