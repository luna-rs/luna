package io.luna.game.event.impl;

/**
 * An event implementation sent when a player clicks an npc's second index.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ObjectSecondClickEvent extends ObjectClickEvent {

    /**
     * Creates a new {@link ObjectSecondClickEvent}.
     */
    public ObjectSecondClickEvent(int id, int x, int y) {
        super(id, x, y);
    }
}
