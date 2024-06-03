package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mob.Mob;

/**
 * An {@link Event} implementation sent when an {@link io.luna.game.model.mob.Mob} dies.
 *
 * @author lare96
 */
public final class DeathEvent extends MobEvent {

    /**
     * The source of the death.
     */
    private final Mob source;

    /**
     * Creates a new {@link MobEvent}.
     *
     * @param mob The mob that died.
     */
    public DeathEvent(Mob mob, Mob source) {
        super(mob);
        this.source = source;
    }

    /**
     * @return The source of the death.
     */
    public Mob getSource() {
        return source;
    }
}
