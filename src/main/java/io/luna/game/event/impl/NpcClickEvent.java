package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mobile.Npc;

/**
 * An event implementation sent when a player clicks any npc index.
 *
 * @author lare96 <http://github.org/lare96>
 */
class NpcClickEvent extends Event {

    /**
     * The instance of the npc.
     */
    private final Npc npc;

    /**
     * Creates a new {@link NpcClickEvent}.
     *
     * @param npc The instance of the npc.
     */
    NpcClickEvent(Npc npc) {
        this.npc = npc;
    }

    /**
     * @return The instance of the npc.
     */
    public Npc getNpc() {
        return npc;
    }
}
