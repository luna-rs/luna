package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * An player-click based event. Not intended for interception.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class PlayerClickEvent extends PlayerEvent {

    /**
     * An event sent when a player clicks an another PLayer's first interaction index.
     *
     * @author lare96 <http://github.org/lare96>
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
    private final int index;

    /**
     * The other player.
     */
    private final Player other;

    /**
     * Creates a new {@link PlayerClickEvent}.
     *
     * @param player The player.
     * @param index The index of the other player.
     * @param other The other player.
     */
    public PlayerClickEvent(Player player, int index, Player other) {
        super(player);
        this.index = index;
        this.other = other;
    }

    /**
     * @return The index of the other player.
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return The other player.
     */
    public Player getOther() {
        return other;
    }
}