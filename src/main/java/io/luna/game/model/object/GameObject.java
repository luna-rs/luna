package io.luna.game.model.object;

import com.google.common.base.MoreObjects;
import io.luna.LunaContext;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.def.ObjectDefinition;

import java.util.Objects;

/**
 * An {@link Entity} implementation representing an object in the Runescape world.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class GameObject extends Entity {

    /**
     * The object identifier.
     */
    private final int id;

    /**
     * The object definition.
     */
    private final ObjectDefinition definition;

    /**
     * @param context The context instance.
     * @param id The object identifier.
     * @param position The spot to spawn this object on.
     */
    public GameObject(LunaContext context, int id, Position position) {
        super(context, EntityType.OBJECT);
        this.id = id;
        definition = ObjectDefinition.ALL.retrieve(id);

        setPosition(position);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof GameObject) {
            GameObject other = (GameObject) obj;
            return id == other.id && position.equals(other.position);
        }
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("id", id).
                add("x", position.getX()).
                add("y", position.getY()).
                add("z", position.getZ()).toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, position);
    }

    @Override
    public int size() {
        return definition.getSize();
    }

    /**
     * @return The object definition.
     */
    public ObjectDefinition getDefinition() {
        return definition;
    }
}