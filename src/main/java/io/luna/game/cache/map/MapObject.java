package io.luna.game.cache.map;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import io.luna.game.model.Position;
import io.luna.game.model.object.ObjectDirection;
import io.luna.game.model.object.ObjectType;

public class MapObject {

    private final int objectId;
    private final Position position;
    private final ObjectType type;
    private final ObjectDirection rotation;

    public MapObject(int objectId, Position position, ObjectType type, ObjectDirection rotation) {
        this.objectId = objectId;
        this.position = position;
        this.type = type;
        this.rotation = rotation;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("objectId", objectId)
                .add("position", position)
                .add("type", type)
                .add("rotation", rotation)
                .toString();
    }

    public int getObjectId() {
        return objectId;
    }

    public Position getPosition() {
        return position;
    }

    public ObjectType getType() {
        return type;
    }

    public ObjectDirection getRotation() {
        return rotation;
    }
}
