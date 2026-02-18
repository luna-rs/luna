package io.luna.game.cache.map;

import io.luna.LunaContext;
import io.luna.game.model.Position;
import io.luna.game.model.object.GameObject;
import io.luna.game.model.object.ObjectDirection;
import io.luna.game.model.object.ObjectType;

/**
 * A single static object placement decoded from the cache.
 * <p>
 * This represents an object definition id placed at a specific {@link Position}, along with its {@link ObjectType}
 * (shape) and {@link ObjectDirection} (rotation).
 * <p>
 * These objects are not "live" entities by themselves; they are typically converted into static {@link GameObject}s
 * during region/map loading.
 *
 * @author lare96
 */
public final class MapObject {

    /**
     * The object definition id.
     */
    private final int objectId;

    /**
     * Absolute world position of the placement.
     */
    private final Position position;

    /**
     * The object type (shape) at this placement.
     */
    private final ObjectType type;

    /**
     * The rotation/orientation of the placed object.
     */
    private final ObjectDirection rotation;

    /**
     * Creates a new {@link MapObject}.
     *
     * @param objectId The object definition id.
     * @param position The absolute placement position.
     * @param type The object type (shape).
     * @param rotation The object rotation/orientation.
     */
    public MapObject(int objectId, Position position, ObjectType type, ObjectDirection rotation) {
        this.objectId = objectId;
        this.position = position;
        this.type = type;
        this.rotation = rotation;
    }

    /**
     * @return The object definition id.
     */
    public int getObjectId() {
        return objectId;
    }

    /**
     * @return The absolute placement position.
     */
    public Position getPosition() {
        return position;
    }

    /**
     * @return The object type (shape).
     */
    public ObjectType getType() {
        return type;
    }

    /**
     * @return The object rotation/orientation.
     */
    public ObjectDirection getRotation() {
        return rotation;
    }

    /**
     * Converts this decoded placement into a static {@link GameObject}.
     *
     * @param context The game context.
     * @return A new static {@link GameObject} instance for this placement.
     */
    public GameObject toGameObject(LunaContext context) {
        return GameObject.createStatic(context, objectId, position, type, rotation);
    }
}
