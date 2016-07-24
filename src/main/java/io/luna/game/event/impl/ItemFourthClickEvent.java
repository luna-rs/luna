package io.luna.game.event.impl;

/**
 * An event implementation sent whenever a player clicks an item's fourth index.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemFourthClickEvent extends ItemClickEvent {

    /**
     * Creates a new {@link ItemFourthClickEvent}.
     */
    public ItemFourthClickEvent(int id, int index, int interfaceId) {
        super(id, index, interfaceId);
    }
}
