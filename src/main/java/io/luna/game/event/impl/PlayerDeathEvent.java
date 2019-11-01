package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;

/**
 * An {@link Event} implementation sent when a player dies.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PlayerDeathEvent extends PlayerEvent {

    /**
     * The source of death.
     */
    private final Mob source;

    /**
     * Creates a new {@link PlayerDeathEvent}.
     */
    public PlayerDeathEvent(Player plr, Mob source) {
        super(plr);
        this.source = source;
    }

    /**
     * @return The source of death.
     */
    public Mob getSource() {
        return source;
    }
}
