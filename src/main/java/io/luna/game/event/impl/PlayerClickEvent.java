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

    private final int index;
    private final Player other;

    public PlayerClickEvent(Player player, int index, Player other) {
        super(player);
        this.index = index;
        this.other = other;
    }

    public int index() {
        return index;
    }

    public Player other() {
        return other;
    }
}