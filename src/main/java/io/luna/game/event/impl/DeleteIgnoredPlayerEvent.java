package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * Occurs when a player attempts to delete someone from their list of ignored players.
 */
public class DeleteIgnoredPlayerEvent extends PlayerEvent {

    public final long ignoredName;

    /**
     * @param player      The player making the delete request.
     * @param ignoredName The name of the ignored player to delete.
     */
    public DeleteIgnoredPlayerEvent(Player player, long ignoredName) {
        super(player);
        this.ignoredName = ignoredName;
    }
}
