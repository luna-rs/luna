package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mob.Mob;

/**
 * A mob-based event. Not intended for interception.
 *
 * @author lare96
 */
public class MobEvent extends Event {

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
    public Mob getMob() {
        return mob;
    }
}
