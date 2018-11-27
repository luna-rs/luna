package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * @author lare96 <http://github.com/lare96>
 */
public final class EquipItemEvent extends PlayerEvent {
    private final int index;
    private final int itemId;
    private final int interfaceId;

    public EquipItemEvent(Player player, int index, int itemId, int interfaceId) {
        super(player);
        this.index = index;
        this.itemId = itemId;
        this.interfaceId = interfaceId;
    }

    public int index() {
        return index;
    }

    public int itemId() {
        return itemId;
    }

    public int interfaceId() {
        return interfaceId;
    }
}