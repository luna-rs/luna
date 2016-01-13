package io.luna.game.model;

/**
 * An enumerated type whose elements represent the states an {@link Entity} can be in.
 *
 * @author lare96 <http://github.org/lare96>
 */
public enum EntityState {

    /**
     * An {@link Entity} has just been instantiated and is awaiting registration.
     */
    IDLE,

    /**
     * An {@link Entity} has just been registered.
     */
    ACTIVE,

    /**
     * An {@link Entity} has just been unregistered.
     */
    INACTIVE
}
