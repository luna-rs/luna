package io.luna.game.event.impl;

import io.luna.game.model.Entity;
import io.luna.game.model.Locatable;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerContextMenuOption;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.injection.InjectableEvent;

/**
 * A player-click based event. Not intended for interception.
 *
 * @author lare96
 */
public class PlayerClickEvent extends PlayerEvent implements ControllableEvent, InteractableEvent, InjectableEvent {

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
        public PlayerSecondClickEvent(Player player, Player targetPlr) {
            super(player, targetPlr);
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
        public PlayerThirdClickEvent(Player player, Player targetPlr) {
            super(player, targetPlr);
        }

        @Override
        public int distance() {
            // todo better way of doing this
            return plr.getContextMenu().contains(PlayerContextMenuOption.FOLLOW) ? Position.VIEWING_DISTANCE :
                    super.distance();
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

    @Override
    public Locatable contextLocatable(Bot bot) {
        return plr;
    }

    /**
     * @return The other player.
     */
    public Player getTargetPlr() {
        return targetPlr;
    }
}