package io.luna.game.model.mob.update;

/**
 * An enum representing update states.
 *
 * @author lare96 <http://github.org/lare96>
 */
public enum UpdateState {

    /**
     * Updating for themself (player updating specific).
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
