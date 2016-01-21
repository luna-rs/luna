package io.luna.game.model;

import io.luna.game.model.mobile.MobileEntity;
import io.luna.game.model.mobile.Player;

/**
 * An enumerated type whose elements represent all of the types an {@link Entity} can be.
 *
 * @author lare96 <http://github.org/lare96>
 */
public enum EntityType {

    /**
     * A ground item that can be picked up by a {@link Player}.
     */
    ITEM,

    /**
     * An object that can be interacted with by a {@link Player}.
     */
    OBJECT,

    /**
     * A {@link MobileEntity} that is controlled by a real person.
     */
    PLAYER,

    /**
     * A non-player controlled {@link MobileEntity}.
     */
    NPC
}
