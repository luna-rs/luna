package io.luna.game.event.impl;

/**
 * An event implementation sent whenever a player clicks an item's second index.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemSecondClickEvent extends ItemClickEvent {

    /**
     * Creates a new {@link ItemSecondClickEvent}.
     */
    public ItemSecondClickEvent(int itemId, int slot, int interfaceId) {
        super(itemId, slot, interfaceId);
    }
}
