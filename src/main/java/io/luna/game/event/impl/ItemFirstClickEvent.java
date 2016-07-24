package io.luna.game.event.impl;

/**
 * An event implementation sent whenever a player clicks an item's first index.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemFirstClickEvent extends ItemClickEvent {

    /**
     * Creates a new {@link ItemFirstClickEvent}.
     */
    public ItemFirstClickEvent(int itemId, int slot, int interfaceId) {
        super(itemId, slot, interfaceId);
    }
}
