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
     * The varbit definition.
     */
    private transient final VarBitDefinition def;

    /**
     * Creates a new {@link Varbit}.
     *
     * @param id    The varbit id.
     * @param value The value to send.
     */
    public Varbit(int id, int value) {
        this.id = id;
        this.value = value;
        def = VarBitDefinition.ALL.retrieve(id);
    }

    /**
     * Packs the value of this varbit into the value of the parent varp.
     *
     * @param parentValue The value of the parent varp.
     * @return The packed value.
     */
    public int pack(int parentValue) {
        int msb = def.getMostSignificantBit();
        int lsb = def.getLeastSignificantBit();
        int max = (1 << (1 + (msb - lsb))) - 1;
        int clearedValue = parentValue & ~(max << lsb);
        return clearedValue | value << lsb;
    }

    /**
     * @return The varbit id.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The value to send.
     */
    public int getValue() {
        return value;
    }

    /**
     * @return The varbit definition.
     */
    public VarBitDefinition getDef() {
        return def;
    }
}
