package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * Occurs when a player attempts to delete someone from their list of friends.
 */
public class DeleteFriendedPlayerEvent extends PlayerEvent {

    public final long friendedName;

    /**
     * @param player       The player performing the request.
     * @param friendedName The name of the friend to delete.
     */
    public DeleteFriendedPlayerEvent(Player player, long friendedName) {
        super(player);
        this.friendedName = friendedName;
    }

}
