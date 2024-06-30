package io.luna.game.model.mob.inter;

/**
 * An enumerated type whose elements represent all possible states for an interface to be in.
 *
 * @author lare96
 */
public enum InterfaceState {

    /**
     * The idle state. Idle is the default state for all interface instances when they are first
     * created.
     */
    IDLE,

    /**
     * The open state. The interface instance is open and viewable on the Player's screen.
     */
    OPEN,

    /**
     * The closed state. The interface instance was taken off the Player's screen.
     */
    CLOSED
}