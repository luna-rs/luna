package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;

/**
 * An {@link Event} implementation sent when an NPC dies.3
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class NpcDeathEvent extends Event {

    /**
     * The npc that died.
     */
    private final Npc npc;

    /**
     * The death source.
     */
    private final Mob source;

    /**
     * Creates a new {@link NpcDeathEvent}.
     */
    public NpcDeathEvent(Npc npc, Mob source) {
        this.npc = npc;
        this.source = source;
    }

    /**
     * @return The npc that died.
     */
    public Npc getNpc() {
        return npc;
    }

    /**
     * @return The death source.
     */
    public Mob getSource() {
        return source;
    }
}
