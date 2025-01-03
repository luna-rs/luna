package io.luna.game.model.mob.varp;

import io.luna.game.model.def.VarBitDefinition;

/**
 * A specialized dataset used by the client that can be encoded within a {@link Varp} type.
 *
 * @author lare96
 */
public final class Varbit {

    /**
     * The varbit id.
     */
    private final int id;

    /**
     * The value to send.
     */
    private final int value;

    /**
     * Creates a new {@link Varbit}.
     *
     * @param id The varbit id.
     * @param value The value to send.
     */
    public Varbit(int id, int value) {
        this.id = id;
        this.value = value;
    }

    /**
     * Prepares this varbit type to be sent to the client as a {@link Varp}.
     */
    public Varp toVarp() {
        VarBitDefinition def = VarBitDefinition.ALL.retrieve(id);
        return new Varp(def.getParentVarpId(), value << def.getLeastSignificantBit());
    }
}
