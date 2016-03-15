package io.luna.game.model.mobile;

import com.google.common.base.MoreObjects;
import io.luna.LunaContext;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.def.NpcDefinition;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;

import java.util.Objects;

/**
 * A mobile entity that is controlled by the server.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Npc extends MobileEntity {

    /**
     * The identifier for this {@code Npc}.
     */
    private final int id;

    /**
     * The definition instance for this {@code Npc}.
     */
    private final NpcDefinition definition;

    /**
     * The identifier for the transformation {@code Npc}.
     */
    private int transformId = -1;

    /**
     * Creates a new {@link Npc}.
     *
     * @param context The context to be managed in.
     * @param id The identifier for this {@code Npc}.
     * @param position The position of this {@code Npc}.
     */
    public Npc(LunaContext context, int id, Position position) {
        super(context);
        this.id = id;
        definition = NpcDefinition.DEFINITIONS[id];
        setPosition(position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getIndex());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("name", definition.getName()).toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Npc) {
            Npc other = (Npc) obj;
            return getIndex() == other.getIndex();
        }
        return false;
    }

    @Override
    public int size() {
        return definition.getSize();
    }

    @Override
    public EntityType type() {
        return EntityType.NPC;
    }

    @Override
    public void resetEntity() {
        transformId = -1;
    }

    /**
     * Transforms this {@code Npc} into another {@code Npc}.
     *
     * @param id The identifier of the {@code Npc} to transform into.
     */
    public void transform(int id) {
        transformId = id;
        updateFlags.flag(UpdateFlag.TRANSFORM);
    }

    /**
     * @return The definition instance for this {@code Npc}.
     */
    public NpcDefinition getDefinition() {
        return definition;
    }

    /**
     * @return The identifier for this {@link Npc}.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The identifier for the transformation {@code Npc}.
     */
    public int getTransformId() {
        return transformId;
    }
}
