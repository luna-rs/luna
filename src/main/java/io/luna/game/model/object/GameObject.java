package io.luna.game.model.object;

import com.google.common.base.MoreObjects;
import io.luna.LunaContext;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.StationaryEntity;
import io.luna.game.model.def.GameObjectDefinition;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessageWriter;
import io.luna.net.msg.out.AddObjectMessageWriter;
import io.luna.net.msg.out.AnimateGameObjectMessageWriter;
import io.luna.net.msg.out.RemoveObjectMessageWriter;

import java.util.Optional;

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
     * @param player The player to update for.
     * @return The dynamic {@link GameObject}.
     */
    public static GameObject createDynamic(LunaContext context, int id, Position position, ObjectType objectType,
                                           ObjectDirection direction, Optional<Player> player) {
        return new GameObject(context, id, position, objectType, direction, player, true);
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
        return new GameObject(context, id, position, objectType, direction, Optional.empty(), false);
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
     * @param player The player to update for.
     * @param dynamic If this object is dynamic.
     */
    public GameObject(LunaContext context, int id, Position position, ObjectType objectType, ObjectDirection direction, Optional<Player> player, boolean dynamic) {
        super(context, position, EntityType.OBJECT, player);
        this.id = id;
        this.objectType = objectType;
        this.direction = direction;
        this.dynamic = dynamic;
        definition = GameObjectDefinition.ALL.get(id).orElse(null);
    }

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this).
                add("id", id).
                add("x", position.getX()).
                add("y", position.getY()).
                add("z", position.getZ()).
                add("type", objectType).
                add("owner", getOwner().map(Player::getUsername).orElse("PUBLIC")).toString();
    }

    @Override
    public final int size() {
        return definition.getSize();
    }

    @Override
    protected final GameMessageWriter showMessage(int offset) {
        return new AddObjectMessageWriter(id, objectType.getId(), direction.getId(), offset);
    }

    @Override
    protected final GameMessageWriter hideMessage(int offset) {
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
                applyUpdate(plr -> {
                    sendPlacementMessage(plr);
                    int offset = getChunk().offset(position);
                    plr.queue(new AnimateGameObjectMessageWriter(offset, objectType.getId(),
                            direction.getId(), animationId));
                });
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
                type == object.type &&
                getOwner().equals(object.getOwner());
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