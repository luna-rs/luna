package io.luna.game.event.impl;

import io.luna.game.model.mobile.Npc;

/**
 * An event implementation sent when a player clicks an npc's third index.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcThirdClickEvent extends NpcClickEvent {

    /**
     * Creates a new {@link NpcClickEvent}.
     */
    public NpcThirdClickEvent(Npc npc) {
        super(npc);
    }
}
