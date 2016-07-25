package io.luna.game.event.impl;

/**
 * An event implementation sent when a player clicks an npc's first index.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ObjectFirstClickEvent extends ObjectClickEvent {

    /**
     * Creates a new {@link ObjectFirstClickEvent}.
     */
    public ObjectFirstClickEvent(int id, int x, int y) {
        super(id, x, y);
    }
}
