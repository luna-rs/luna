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
public class GameObject extends StationaryEntity {

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
     * The object's definition.
     */
    private final ObjectDefinition definition;

    /**
     * If this object is dynamic.
     */
    private final boolean dynamic;

    /**
     * Creates a new {@link GameObject} that can be either static or dynamic.
     *
     * @param context The context instance.
     * @param id The identifier.
     * @param position The position.
     * @param objectType The type.
     * @param direction The direction.
     * @param player The player to update for.
     * @param dynamic If this object is dynamic.
     */
    public GameObject(LunaContext context, int id, Position position, ObjectType objectType, ObjectDirection direction, Optional<Player> player, boolean dynamic) {
        super(context, position, EntityType.OBJECT, player);
        this.id = id;
        this.objectType = objectType;
        this.direction = direction;
        this.dynamic = dynamic;
        definition = ObjectDefinition.ALL.retrieve(id);
    }

    /**
     * Creates a new dynamic {@link GameObject}.
     *
     * @param context The context instance.
     * @param id The identifier.
     * @param position The position.
     * @param objectType The type.
     * @param direction The direction.
     * @param player The player to update for.
     */
    public GameObject(LunaContext context, int id, Position position, ObjectType objectType, ObjectDirection direction, Optional<Player> player) {
        this(context, id, position, objectType, direction, player, true);
    }

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this).
                add("id", id).
                add("x", position.getX()).
                add("y", position.getY()).
                add("z", position.getZ()).
                add("type", objectType).
                add("owner", getOwner().map(Player::getUsername).orElse("NULL/PUBLIC")).toString();
    }

    @Override
    public final int size() {
        return definition.getSize();
    }

    @Override
    protected final GameMessageWriter showMessage(int offset) {
        int type = objectType.getId() << 2;
        int orientation = direction.getId() & 3;
        return new AddObjectMessageWriter(id, type, orientation, offset);
    }

    @Override
    protected final GameMessageWriter hideMessage(int offset) {
        int type = objectType.getId() << 2;
        int orientation = direction.getId() & 3;
        return new RemoveObjectMessageWriter(type, orientation, offset);
    }

    /**
     * Determines if this object will replace {@code object} on the map and vice-versa.
     *
     * @param object The object to check.
     */
    public boolean replaces(GameObject object) {
        return position.equals(object.position) &&
                type == object.type;
    }

    /**
     * @return The identifier.
     */
    public final int getId() {
        return id;
    }

    /**
     * @return The type.
     */
    public final ObjectType getObjectType() {
        return objectType;
    }

    /**
     * @return The direction.
     */
    public final ObjectDirection getDirection() {
        return direction;
    }

    /**
     * @return The definition.
     */
    public final ObjectDefinition getDefinition() {
        return definition;
    }

    /**
     * Returns if this object was spawned by the server. Objects loaded from the cache will return {@code false}.
     *
     * @return {@code true} if this object is dynamic.
     */
    public final boolean isDynamic() {
        return dynamic;
    }
}