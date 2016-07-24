package io.luna.game.event.impl;

import io.luna.game.model.mobile.Npc;

/**
 * An event implementation sent when a player clicks an npc's fourth index.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcFourthClickEvent extends NpcClickEvent {

    /**
     * Creates a new {@link NpcClickEvent}.
     */
    public NpcFourthClickEvent(Npc npc) {
        super(npc);
    }
}
