package io.luna.game.model;

import com.google.common.collect.Sets;

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
    ITEM,

    /**
     * A game object.
     */
    OBJECT,

    /**
     * A player mob.
     */
    PLAYER,

    /**
     * A non-player mob.
     */
    NPC,

    /**
     * A local non-registered projectile.
     */
    PROJECTILE,

    /**
     * A local non-registered graphic.
     */
    GRAPHIC,

    /**
     * A local non-registered sound.
     */
    SOUND;

    /**
     * An immutable enum set of these values.
     */
    public static final Set<EntityType> ALL = Sets.immutableEnumSet(EnumSet.allOf(EntityType.class));

}
