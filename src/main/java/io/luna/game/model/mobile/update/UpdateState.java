package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.MobileEntity;
import io.luna.game.model.mobile.Player;

/**
 * An enumerated type whose elements represent the updating states.
 *
 * @author lare96 <http://github.org/lare96>
 */
public enum UpdateState {

    /**
     * A {@link Player} is updating for themself, only relevant for {@code Player} updating.
     */
    UPDATE_SELF,

    /**
     * A {@link Player} is updating for the {@link MobileEntity}s around them.
     */
    UPDATE_LOCAL,

    /**
     * A {@link Player} is adding new {@link MobileEntity}s that have just appeared around them.
     */
    ADD_LOCAL
}
