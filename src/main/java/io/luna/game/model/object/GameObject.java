package io.luna.game.model.object;

import com.google.common.base.MoreObjects;
import io.luna.LunaContext;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.StationaryEntity;
import io.luna.game.model.def.ObjectDefinition;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessageWriter;
import io.luna.net.msg.out.AddObjectMessageWriter;
import io.luna.net.msg.out.RemoveObjectMessageWriter;

import java.util.Optional;

/**
 * An {@link Entity} implementation representing an object in the Runescape world.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class GameObject extends StationaryEntity {

    /**
     * The identifier.
     */
    private final int id;

    /**
     * The type.
     */
    private final ObjectType objectType;

    /**
     * The direction.
     */
    private final ObjectDirection direction;

    /**
     * The  definition.
     */
    private final ObjectDefinition definition;

    /**
     * Creates a new {@link GameObject}.
     *
     * @param context The context instance.
     * @param id The identifier.
     * @param position The position.
     * @param objectType The type.
     * @param direction The direction.
     * @param player The player to update for.
     */
    public GameObject(LunaContext context, int id, Position position, ObjectType objectType, ObjectDirection direction, Optional<Player> player) {
        super(context, position, EntityType.OBJECT, player);
        this.id = id;
        this.objectType = objectType;
        this.direction = direction;
        definition = ObjectDefinition.ALL.retrieve(id);
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("type", objectType).
                add("x", position.getX()).
                add("y", position.getY()).
                add("z", position.getZ()).toString();
    }

    @Override
    public int size() {
        return definition.getSize();
    }

    @Override
    protected GameMessageWriter showMessage(int offset) {
        int type = objectType.getId() << 2;
        int orientation = direction.getId() & 3;
        return new AddObjectMessageWriter(id, type, orientation, offset);
    }

    @Override
    protected GameMessageWriter hideMessage(int offset) {
        int type = objectType.getId() << 2;
        int orientation = direction.getId() & 3;
        return new RemoveObjectMessageWriter(type, orientation, offset);
    }

    /**
     * @return The identifier.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The type.
     */
    public ObjectType getObjectType() {
        return objectType;
    }

    /**
     * @return The direction.
     */
    public ObjectDirection getDirection() {
        return direction;
    }

    /**
     * @return The definition.
     */
    public ObjectDefinition getDefinition() {
        return definition;
    }
}