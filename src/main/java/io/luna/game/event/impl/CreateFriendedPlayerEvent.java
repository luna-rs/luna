package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * This event is generated when a player attempts to add someone to their friends list.
 */
public class CreateFriendedPlayerEvent extends PlayerEvent {
    /**
     * The name of the player to add.
     */
    public final long friendedName;

    public CreateFriendedPlayerEvent(Player player, long friendedName) {
        super(player);
        this.friendedName = friendedName;
    }
}
