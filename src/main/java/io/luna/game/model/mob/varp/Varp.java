package io.luna.game.model.mob.varp;

/**
 * A model representing a varP which stands for VarPlayer.
 *
 * @author lare96
 */
public class Varp {

    private final int id;
    private final int value;

    public Varp(int id, int value) {
        this.id = id;
        this.value = value;
    }

    public Varp(int id, boolean value) {
        this(id, value ? 1 : 0);
    }

    public int getId() {
        return id;
    }

    public int getValue() {
        return value;
    }
}
