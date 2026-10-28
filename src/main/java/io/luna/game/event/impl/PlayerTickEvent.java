package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * A game-thread player pulse after movement, actions and bot decisions, before synchronization clears movement flags.
 * Per-player content can observe the final position and teleport state without polling an earlier world task.
 *
 * @author lare96
 */
public final class PlayerTickEvent extends PlayerEvent {

    /**
     * Creates the pulse for the player whose current turn has finished.
     *
     * @param player The active player being processed.
     */
    public PlayerTickEvent(Player player) {
        super(player);
    }
}
