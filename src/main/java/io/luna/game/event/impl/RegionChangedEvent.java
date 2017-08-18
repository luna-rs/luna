package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * An event sent when a player changes regions.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class RegionChangedEvent extends PlayerEvent {

    /**
     * Creates a new {@link RegionChangedEvent}.
     *
     * @param player The player.
     */
    public RegionChangedEvent(Player player) {
        super(player);
    }
}
