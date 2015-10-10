package io.luna.game.model.mobile;

import io.luna.LunaContext;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.def.NpcDefinition;

import java.util.Objects;

import com.google.common.base.MoreObjects;

/**
 * A mobile entity that is controlled by the server.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class Npc extends MobileEntity {

    /**
     * The identifier for this {@link Npc}.
     */
    private final int id;

    /**
     * Creates a new {@link Npc}.
     *
     * @param context The context to be managed in.
     * @param id The identifier for this {@code Npc}.
     * @param position The position of this {@code Npc}.
     */
    public Npc(LunaContext context, int id, Position position) {
        super(context, position);
        this.id = id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getIndex());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("name", definition().getName()).toString();
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
        return definition().getSize();
    }

    @Override
    public EntityType type() {
        return EntityType.NPC;
    }

    /**
     * @return The {@link NpcDefinition} for this {@code Npc}.
     */
    public NpcDefinition definition() {
        return NpcDefinition.DEFINITIONS[id];
    }

    /**
     * @return The identifier for this {@link Npc}.
     */
    public int getId() {
        return id;
    }
}
