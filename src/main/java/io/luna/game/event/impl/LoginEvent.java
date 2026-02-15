package io.luna.game.event.impl;

import io.luna.game.model.Locatable;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.bot.Bot;

/**
 * An event sent when a player logs in.
 *
 * @author lare96
 */
public final class LoginEvent extends PlayerEvent implements InjectableEvent {

    /**
     * Creates a new {@link LoginEvent}.
     *
     * @param player The player.
     */
    public LoginEvent(Player player) {
        super(player);
    }

    @Override
    public Locatable contextLocatable(Bot bot) {
        return plr;
    }
}
