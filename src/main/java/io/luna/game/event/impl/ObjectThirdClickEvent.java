package io.luna.game.event.impl;

/**
 * An event implementation sent when a player clicks an object's third index.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ObjectThirdClickEvent extends ObjectClickEvent {

    /**
     * Creates a new {@link ObjectThirdClickEvent}.
     */
    public ObjectThirdClickEvent(int id, int x, int y) {
        super(id, x, y);
    }
}
