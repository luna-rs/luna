package io.luna.game.model.mob.varp;

import io.luna.game.model.def.VarBitDefinition;

/**
 * A type of {@link Varp} that encodes
 */
public class Varbit extends Varp {
    private static int packValue(int varbitId, int value) {
        return 0;
    }

    private final VarBitDefinition def;

    public Varbit(int varbitId, int varpId, int value) {
        super(varpId, packValue(varbitId, value));
        def = VarBitDefinition.ALL.retrieve(varbitId);
    }

    public VarBitDefinition getDef() {
        return def;
    }
}
