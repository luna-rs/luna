package io.luna.game.model.mob.block;

/**
 * An enum representing update states.
 *
 * @author lare96
 */
public enum UpdateState {

    /**
     * Updating for themselves.
     */
    UPDATE_SELF,

    /**
     * Updating existing local mobs.
     */
    UPDATE_LOCAL,

    /**
     * Adding new local mobs.
     */
    ADD_LOCAL
}
