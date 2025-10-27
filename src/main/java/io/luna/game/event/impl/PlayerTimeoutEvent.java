package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * A {@link PlayerEvent} implementation sent when the player is idle for a long period of time.
 *
 * @author lare96
 */
public final class PlayerTimeoutEvent extends PlayerEvent {

    /**
     * Creates a new {@link PlayerEvent}.
     *
     * @param plr The player.
     */
    public PlayerTimeoutEvent(Player plr) {
        super(plr);
    }
}
