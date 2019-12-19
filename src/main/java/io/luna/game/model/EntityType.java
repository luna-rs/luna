package io.luna.game.model;

import com.google.common.collect.Sets;

import java.util.EnumSet;
import java.util.Set;

/**
 * An enum representing different entity types.
 *
 * @author lare96 <http://github.org/lare96>
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
    NPC;

    /**
     * An immutable enum set of these values.
     */
    public static final Set<EntityType> ALL = Sets.immutableEnumSet(EnumSet.allOf(EntityType.class));
    
}
