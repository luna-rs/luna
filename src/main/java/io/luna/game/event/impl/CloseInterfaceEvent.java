package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * A {@link PlayerEvent} implementation sent when a player closes an interface.
 */
public final class CloseInterfaceEvent extends PlayerEvent {

    /**
     * Creates a new {@link PlayerEvent}.
     *
     * @param plr The player.
     */
    public CloseInterfaceEvent(Player plr) {
        super(plr);
    }
}
