package io.luna.game.event.impl;

/**
 * An event implementation sent whenever a player clicks an item's fifth index.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemFifthClickEvent extends ItemClickEvent {

    /**
     * Creates a new {@link ItemFifthClickEvent}.
     */
    public ItemFifthClickEvent(int id, int index, int interfaceId) {
        super(id, index, interfaceId);
    }
}
