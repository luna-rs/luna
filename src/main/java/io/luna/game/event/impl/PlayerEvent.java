package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mob.Player;

/**
 * A player-based event. Not intended for interception.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class PlayerEvent extends Event {

    /**
     * The player.
     */
    protected final Player plr;

    /**
     * Creates a new {@link PlayerEvent}.
     *
     * @param plr The player.
     */
    public PlayerEvent(Player plr) {
        this.plr = plr;
    }

    /**
     * Creates a new {@link PlayerEvent}.
     *
     * @param plr The player.
     * @param mapId The identifier for this event.
     */
    public PlayerEvent(Player plr, int mapId) {
        super(mapId);
        this.plr = plr;
    }

    /**
     * @return The player.
     */
    public Player getPlr() {
        return plr;
    }
}
