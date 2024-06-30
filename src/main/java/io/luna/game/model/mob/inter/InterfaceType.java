package io.luna.game.model.mob.inter;

/**
 * An enumerated type representing the {@code 3} main types of interfaces.
 *
 * @author lare96 
 */
public enum InterfaceType {

    /**
     * The standard interface.
     */
    STANDARD,

    /**
     * The input interface. Remains open alongside {@link #STANDARD} interfaces, and is used to obtain
     * Player input.
     */
    INPUT,

    /**
     * The walkable interface. Allows the interface to remain open during movement, as well as remain open
     * alongside {@link #STANDARD} interfaces.
     */
    WALKABLE
}