package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * An event sent when the client loads a new region.
 *
 * @author lare96
 */
public final class RegionLoadedEvent extends PlayerEvent {

    /**
     * Creates a new {@link RegionLoadedEvent}.
     *
     * @param player The player.
     */
    public RegionLoadedEvent(Player player) {
        super(player);
    }
}
