package io.luna.game.model.def;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.object.GameObject;

/**
 * A child definition containing varp data associated with either {@link Npc} or {@link GameObject} transformations.
 *
 * @author lare96
 */
public final class VarpChildDefinition {

    /**
     * The varbit id.
     */
    private final int varBitId;

    /**
     * The varp id.
     */
    private final int varpId;

    /**
     * The child ids.
     */
    private final ImmutableList<Integer> childIdList;

    /**
     * Creates a new {@link VarpChildDefinition}.
     *
     * @param varBitId The varbit id.
     * @param varpId The varp id.
     * @param childIdList The child ids.
     */
    public VarpChildDefinition(int varBitId, int varpId, ImmutableList<Integer> childIdList) {
        this.varBitId = varBitId;
        this.varpId = varpId;
        this.childIdList = childIdList;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("varBitId", varBitId)
                .add("varpId", varpId)
                .add("childIdList", childIdList)
                .toString();
    }

    /**
     * @return The varbit id.
     */
    public int getVarBitId() {
        return varBitId;
    }

    /**
     * @return The varp id.
     */
    public int getVarpId() {
        return varpId;
    }

    /**
     * @return The child ids.
     */
    public ImmutableList<Integer> getChildIdList() {
        return childIdList;
    }
}
