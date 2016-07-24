package io.luna.game.event.impl;

import io.luna.game.model.mobile.Npc;

/**
 * An event implementation sent when a player clicks an npc's second index.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcSecondClickEvent extends NpcClickEvent {

    /**
     * Creates a new {@link NpcSecondClickEvent}.
     */
    public NpcSecondClickEvent(Npc npc) {
        super(npc);
    }
}
