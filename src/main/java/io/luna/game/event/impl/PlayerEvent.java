package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mob.Player;

/**
 * A player-based event. Not intended for interception.
 *
 * @author lare96 <http://github.org/lare96>
 */
class PlayerEvent extends Event {

    /**
     * The player.
     */
    protected final Player player;

    /**
     * Creates a new {@link PlayerEvent}.
     *
     * @param player The player.
     */
    public PlayerEvent(Player player) {
        this.player = player;
    }

    /**
     * Creates a new {@link PlayerEvent}.
     *
     * @param player The player.
     * @param mapId The identifier for this event.
     */
    public PlayerEvent(Player player, int mapId) {
        super(mapId);
        this.player = player;
    }

    /**
     * @return The player.
     */
    public Player plr() {
        return player;
    }
}
