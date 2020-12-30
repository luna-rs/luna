package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * Occurs when a player attempts to ignore someone.
 */
public class CreateIgnoredPlayerEvent extends PlayerEvent {

    public final long ignoredName;

    /**
     * @param player      The player performing the request.
     * @param ignoredName The name of the player being ignored.
     */
    public CreateIgnoredPlayerEvent(Player player, long ignoredName) {
        super(player);
        this.ignoredName = ignoredName;
    }
}
