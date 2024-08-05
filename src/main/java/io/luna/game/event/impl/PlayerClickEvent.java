package io.luna.game.event.impl;

import io.luna.game.model.Entity;
import io.luna.game.model.mob.Player;

/**
 * A player-click based event. Not intended for interception.
 *
 * @author lare96
 */
public class PlayerClickEvent extends PlayerEvent implements ControllableEvent, InteractableEvent {

    /**
     * An event sent when a player clicks antargetPlr player's fourth interaction index.
     *
     * @author lare96
     */
    public static final class PlayerFirstClickEvent extends PlayerClickEvent {

        /**
         * Creates a new {@link PlayerFourthClickEvent}.
         */
        public PlayerFirstClickEvent(Player player, Player targetPlr) {
            super(player, targetPlr);
        }
    }

    /**
     * An event sent when a player clicks antargetPlr player's fourth interaction index.
     *
     * @author lare96
     */
    public static final class PlayerSecondClickEvent extends PlayerClickEvent {

        /**
         * Creates a new {@link PlayerFourthClickEvent}.
         */
        public PlayerSecondClickEvent(Player player,  Player targetPlr) {
            super(player,  targetPlr);
        }
    }

    /**
     * An event sent when a player clicks antargetPlr player's fourth interaction index.
     *
     * @author lare96
     */
    public static final class PlayerThirdClickEvent extends PlayerClickEvent {

        /**
         * Creates a new {@link PlayerFourthClickEvent}.
         */
        public PlayerThirdClickEvent(Player player,  Player targetPlr) {
            super(player, targetPlr);
        }
    }

    /**
     * An event sent when a player clicks antargetPlr player's fourth interaction index.
     *
     * @author lare96
     */
    public static final class PlayerFourthClickEvent extends PlayerClickEvent {

        /**
         * Creates a new {@link PlayerFourthClickEvent}.
         */
        public PlayerFourthClickEvent(Player player, Player targetPlr) {
            super(player, targetPlr);
        }
    }
    /**
     * An event sent when a player clicks antargetPlr player's fourth interaction index.
     *
     * @author lare96
     */
    public static final class PlayerFifthClickEvent extends PlayerClickEvent {

        /**
         * Creates a new {@link PlayerFourthClickEvent}.
         */
        public PlayerFifthClickEvent(Player player, Player targetPlr) {
            super(player, targetPlr);
        }
    }

    /**
     * The other player.
     */
    private final Player targetPlr;

    /**
     * Creates a new {@link PlayerClickEvent}.
     *
     * @param player The player.
     * @param targetPlr The other player.
     */
    public PlayerClickEvent(Player player, Player targetPlr) {
        super(player);
        this.targetPlr = targetPlr;
    }

    @Override
    public Entity target() {
        return targetPlr;
    }

    /**
     * @return The other player.
     */
    public Player getTargetPlr() {
        return targetPlr;
    }
}