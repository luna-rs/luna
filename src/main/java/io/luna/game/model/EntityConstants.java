package io.luna.game.model;

import io.luna.game.model.mobile.Player;

/**
 * A collection of constants related to {@link Entity}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EntityConstants {

    /**
     * The maximum distance that a {@link Player} can view.
     */
    public static final int VIEWING_DISTANCE = 15;

    /**
     * A private constructor to discourage external instantiation.
     */
    private EntityConstants() {
    }
}
