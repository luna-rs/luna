package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mobile.Player;

/**
 * An {@link Event} implementation sent whenever a {@link Player} changes regions.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class RegionChangedEvent extends Event {

    /**
     * Singleton instance.
     */
    public static final Event INSTANCE = new RegionChangedEvent();

    /**
     * Private constructor to discourage external instantiation.
     */
    private RegionChangedEvent() {
    }
}
