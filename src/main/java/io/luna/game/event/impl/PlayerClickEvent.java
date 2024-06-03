package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.controller.ControllableEvent;

/**
 * A player-click based event. Not intended for interception.
 *
 * @author lare96
 */
public class PlayerClickEvent extends PlayerEvent implements ControllableEvent {

    /**
     * An event sent when a player clicks another player's fourth interaction index.
     *
     * @author lare96
     */
    public static final class PlayerFourthClickEvent extends PlayerClickEvent {

        /**
         * Creates a new {@link PlayerFourthClickEvent}.
         */
        public PlayerFourthClickEvent(Player player, int index, Player other) {
            super(player, index, other);
        }
    }

    /**
     * The index of the other player.
     */
    private final int targetIndex;

    /**
     * The other player.
     */
    private final Player target;

    /**
     * Creates a new {@link PlayerClickEvent}.
     *
     * @param player The player.
     * @param targetIndex The index of the other player.
     * @param target The other player.
     */
    public PlayerClickEvent(Player player, int targetIndex, Player target) {
        super(player);
        this.targetIndex = targetIndex;
        this.target = target;
    }

    /**
     * @return The index of the other player.
     */
    public int getTargetIndex() {
        return targetIndex;
    }

    /**
     * @return The other player.
     */
    public Player getTarget() {
        return target;
    }
}