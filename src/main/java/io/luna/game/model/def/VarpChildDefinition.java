package io.luna.game.model.def;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.object.GameObject;

/**
 * A small “child definition” that describes varp/varbit-driven transformations for {@link Npc}s and {@link GameObject}s.
 * <p>
 * In the RuneScape cache, certain NPCs/objects can “morph” (transform) into different ids depending on a client varp
 * (variable parameter) or a varbit (bitfield packed into a varp). This class captures the data needed to select the
 * correct transformed id:
 * <ul>
 *     <li>{@link #varBitId} - the varbit id to read (often preferred when present)</li>
 *     <li>{@link #varpId} - the varp id to read when no varbit is used</li>
 *     <li>{@link #childIdList} - the list of potential transformed ids (“children”)</li>
 * </ul>
 *
 * @author lare96
 */
public final class VarpChildDefinition {

    /**
     * The varbit id used to drive the transformation, or a sentinel value if no varbit is used.
     */
    private final int varBitId;

    /**
     * The varp id used to drive the transformation, or a sentinel value if no varp is used.
     */
    private final int varpId;

    /**
     * The candidate transformed ids (“children”) that this entity may morph into.
     */
    private final ImmutableList<Integer> childIdList;

    /**
     * Creates a new {@link VarpChildDefinition}.
     *
     * @param varBitId The varbit id used for selection (or a sentinel if unused).
     * @param varpId The varp id used for selection (or a sentinel if unused).
     * @param childIdList The candidate child ids the entity may transform into.
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
     * Returns the varbit id used to drive this transformation.
     *
     * @return The varbit id (or sentinel if unused).
     */
    public int getVarBitId() {
        return varBitId;
    }

    /**
     * Returns the varp id used to drive this transformation.
     *
     * @return The varp id (or sentinel if unused).
     */
    public int getVarpId() {
        return varpId;
    }

    /**
     * Returns the candidate transformed ids (“children”).
     *
     * @return The immutable list of child ids.
     */
    public ImmutableList<Integer> getChildIdList() {
        return childIdList;
    }
}
