package io.luna.game.model;

/**
 * An enumerated type that represents all of the possible entity types.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public enum EntityType {

    /**
     * A ground item that can be picked up by a player.
     */
    ITEM,

    /**
     * An object that can be interacted with by a player.
     */
    OBJECT,

    /**
     * A mobile entity that is controlled by a real person.
     */
    PLAYER,

    /**
     * A non-player controlled mobile entity that is controlled by the server.
     */
    NPC
}
