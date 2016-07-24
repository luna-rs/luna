package io.luna.game.event.impl;

/**
 * An event implementation sent whenever a player clicks an item's third index.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemThirdClickEvent extends ItemClickEvent {

    /**
     * Creates a new {@link ItemThirdClickEvent}.
     */
    public ItemThirdClickEvent(int itemId, int slot, int interfaceId) {
        super(itemId, slot, interfaceId);
    }
}
