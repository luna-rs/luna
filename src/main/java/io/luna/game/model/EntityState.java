package io.luna.game.model;

/**
 * An enum representing states an entity can be in.
 *
 * @author lare96
 */
public enum EntityState {

    /**
     * An entity is awaiting registration.
     */
    NEW,

    /**
     * An entity has been registered.
     */
    ACTIVE,

    /**
     * An entity has been unregistered.
     */
    INACTIVE
}
