package io.luna.game.model.object;

import com.google.common.base.MoreObjects;
import io.luna.LunaContext;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.StationaryEntity;
import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.chunk.ChunkUpdatableRequest;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.game.model.def.GameObjectDefinition;
import io.luna.net.msg.out.AddObjectMessageWriter;
import io.luna.net.msg.out.AnimateGameObjectMessageWriter;
import io.luna.net.msg.out.RemoveObjectMessageWriter;


/**
 * A {@link StationaryEntity} representing an object on the map.
 *
 * @author lare96
 */
public class GameObject extends StationaryEntity {

    /**
     * Creates a dynamic {@link GameObject}. Dynamic objects are spawned and deleted by the server as needed.
     *
     * @param context The context instance.
     * @param id The identifier.
     * @param position The position.
     * @param objectType The type.
     * @param direction The direction.
     * @param view WHo this object is viewable to.
     * @return The dynamic {@link GameObject}.
     */
    public static GameObject createDynamic(LunaContext context, int id, Position position, ObjectType objectType,
                                           ObjectDirection direction, ChunkUpdatableView view) {
        return new GameObject(context, id, position, objectType, direction, view, true);
    }

    /**
     * Creates a static {@link GameObject}. Static objects exist natively and are updated for everyone by default.
     *
     * @param context The context instance.
     * @param id The identifier.
     * @param position The position.
     * @param objectType The type.
     * @param direction The direction.
     * @return The static {@link GameObject}.
     */
    public static GameObject createStatic(LunaContext context, int id, Position position, ObjectType objectType,
                                          ObjectDirection direction) {
        return new GameObject(context, id, position, objectType, direction, ChunkUpdatableView.globalView(), false);
    }

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
    private final GameObjectDefinition definition;

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
     * @param view Who this object is viewable to.
     * @param dynamic If this object is dynamic.
     */
    public GameObject(LunaContext context, int id, Position position, ObjectType objectType, ObjectDirection direction, ChunkUpdatableView view, boolean dynamic) {
        super(context, position, EntityType.OBJECT, view);
        this.id = id;
        this.objectType = objectType;
        this.direction = direction;
        this.dynamic = dynamic;
        definition = GameObjectDefinition.ALL.get(id).orElse(null);
        if (!dynamic) {
            setHidden(false);
        }
    }

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this).
                add("id", id).
                add("x", position.getX()).
                add("y", position.getY()).
                add("z", position.getZ()).
                add("type", objectType).
                add("view", getView()).toString();
    }

    @Override
    public ChunkUpdatableView computeCurrentView() {
        return getView();
    }

    @Override
    public final int size() {
        return definition.getSize();
    }

    @Override
    public final int sizeX() {
        return definition.getSizeX();
    }

    @Override
    public final int sizeY() {
        return definition.getSizeY();
    }

    @Override
    protected final ChunkUpdatableMessage showMessage(int offset) {
        return new AddObjectMessageWriter(id, objectType.getId(), direction.getId(), offset);
    }

    @Override
    protected final ChunkUpdatableMessage hideMessage(int offset) {
        return new RemoveObjectMessageWriter(objectType.getId(), direction.getId(), offset);
    }

    /**
     * Animates this object.
     */
    public void animate() {
        if (!isHidden()) {
            int animationId = definition.getAnimationId().orElseThrow(() ->
                    new IllegalStateException("Object [" + id + "] does not have an animation!"));
            if (animationId > 0) {
                int offset = getChunk().offset(position);
                AnimateGameObjectMessageWriter msg = new AnimateGameObjectMessageWriter(offset, objectType.getId(),
                        direction.getId(), animationId);
                chunkRepository.queueUpdate(new ChunkUpdatableRequest(this, msg, false));
            }
        }
    }

    /**
     * Determines if this object will replace {@code object} on the map and vice-versa.
     *
     * @param object The object to check.
     */
    public boolean replaces(GameObject object) {
        return position.equals(object.position) &&
                objectType == object.objectType &&
                getView().equals(object.getView());
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
    public final GameObjectDefinition getDefinition() {
        return definition;
    }

    /**
     * @return {@code true} if this object was spawned by the server. Objects loaded from the cache will return
     * {@code false}.
     */
    public final boolean isDynamic() {
        return dynamic;
    }

}