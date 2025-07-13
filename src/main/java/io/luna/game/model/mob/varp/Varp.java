package io.luna.game.model.mob.varp;

import io.luna.game.model.def.VarpDefinition;

/**
 * A model representing a varP which stands for VarPlayer. They hold player related client values that can be changed
 * by the server.
 *
 * @author lare96
 */
public class Varp {

    /**
     * The varp id.
     */
    private final int id;

    /**
     * The varp value.
     */
    private final int value;

    /**
     * The varp definition.
     */
    private final VarpDefinition def;

    /**
     * Creates a new {@link Varp}.
     *
     * @param id    The id.
     * @param value The value.
     */
    public Varp(int id, int value) {
        this.id = id;
        this.value = value;
        def = VarpDefinition.ALL.retrieve(id);
    }

    /**
     * @return The varp id.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The varp value.
     */
    public int getValue() {
        return value;
    }

    /**
     * @return The varp definition.
     */
    public VarpDefinition getDef() {
        return def;
    }
}
