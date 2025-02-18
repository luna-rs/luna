package io.luna.game.model;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;

import java.util.EnumSet;
import java.util.Set;

/**
 * An enum representing different entity types.
 *
 * @author lare96
 */
public enum EntityType {

    /**
     * A ground item.
     */
    ITEM(GroundItem.class),

    /**
     * A game object.
     */
    OBJECT(GameObject.class),

    /**
     * A player mob.
     */
    PLAYER(Player.class),

    /**
     * A non-player mob.
     */
    NPC(Npc.class),

    /**
     * A local non-registered projectile.
     */
    PROJECTILE(LocalProjectile.class),

    /**
     * A local non-registered graphic.
     */
    GRAPHIC(LocalGraphic.class),

    /**
     * A local non-registered sound.
     */
    SOUND(LocalSound.class);

    /**
     * An immutable enum set of these values.
     */
    public static final Set<EntityType> ALL = Sets.immutableEnumSet(EnumSet.allOf(EntityType.class));

    /**
     * An immutable map of class types to matching entity types.
     */
    public static final ImmutableMap<Class<?>, EntityType> CLASS_TO_TYPE;

    static {
        ImmutableMap.Builder<Class<?>, EntityType> builder = ImmutableMap.builder();
        for (EntityType type : values()) {
            builder.put(type.classType, type);
        }
        CLASS_TO_TYPE = builder.build();
    }

    /**
     * The class type.
     */
    private final Class<?> classType;

    /**
     * Creates a new {@link EntityType}.
     *
     * @param type The class type.
     */
    EntityType(Class<?> type) {
        this.classType = type;
    }

    /**
     * @return The class type.
     */
    public Class<?> getClassType() {
        return classType;
    }
}
