package io.luna.game.cache.map;

import io.luna.LunaContext;
import io.luna.game.model.Position;
import io.luna.game.model.object.GameObject;
import io.luna.game.model.object.ObjectDirection;
import io.luna.game.model.object.ObjectType;

/**
 * Represents a static {@link GameObject} on the map that was decoded from the cache.
 *
 * @author lare96
 */
public final class MapObject {

    /**
     * The object id.
     */
    private final int objectId;

    /**
     * The object position.
     */
    private final Position position;

    /**
     * The object type.
     */
    private final ObjectType type;

    /**
     * The object direction.
     */
    private final ObjectDirection rotation;

    /**
     * Creates a new {@link MapObject}.
     *
     * @param objectId The object id.
     * @param position The object position.
     * @param type The object type.
     * @param rotation The object direction.
     */
    public MapObject(int objectId, Position position, ObjectType type, ObjectDirection rotation) {
        this.objectId = objectId;
        this.position = position;
        this.type = type;
        this.rotation = rotation;
    }

    /**
     * @return The object id.
     */
    public int getObjectId() {
        return objectId;
    }

    /**
     * @return The object position.
     */
    public Position getPosition() {
        return position;
    }

    /**
     * @return The object type.
     */
    public ObjectType getType() {
        return type;
    }

    /**
     * @return The object direction.
     */
    public ObjectDirection getRotation() {
        return rotation;
    }

    /**
     * Converts this map object into a static {@link GameObject}.
     */
    public GameObject toGameObject(LunaContext context) {
        return GameObject.createStatic(context, objectId, position, type, rotation);
    }
}
