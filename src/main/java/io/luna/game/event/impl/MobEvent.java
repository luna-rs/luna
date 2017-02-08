package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mobile.Mob;
import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.Player;

/**
 * A mob-based event. Not intended for interception.
 *
 * @author lare96 <http://github.org/lare96>
 */
class MobEvent extends Event {

    /**
     * The mob.
     */
    protected final Mob mob;

    /**
     * Creates a new {@link MobEvent}.
     *
     * @param mob The mob.
     */
    public MobEvent(Mob mob) {
        this.mob = mob;
    }

    /**
     * @return The mob.
     */
    public Mob mob() {
        return mob;
    }

    /**
     * Returns the mob as a non-player.
     */
    public Npc npc() {
        return (Npc) mob;
    }

    /**
     * Returns the mob as a player.
     */
    public Player plr() {
        return (Player) mob;
    }
}
