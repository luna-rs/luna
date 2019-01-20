package io.luna.game.model;

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
    public static final Set<EntityType> ALL = Set.copyOf(EnumSet.allOf(EntityType.class));
}
