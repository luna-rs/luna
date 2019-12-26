package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;

/**
 * An event sent when a player uses an item on an object.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemOnObjectEvent extends PlayerEvent {

    /**
     * The item identifier.
     */
    private final int itemId;

    /**
     * The item index.
     */
    private final int itemIndex;

    /**
     * The item interface identifier.
     */
    private final int itemInterfaceId;

    /**
     * The object.
     */
    private final GameObject gameObject;

    /**
     * Creates a new {@link ItemOnObjectEvent}.
     *
     * @param player The player.
     * @param itemId The item identifier.
     * @param itemIndex The item index.
     * @param itemInterfaceId The item interface identifier.
     * @param gameObject The object.
     */
    public ItemOnObjectEvent(Player player, int itemId, int itemIndex, int itemInterfaceId, GameObject gameObject) {
        super(player);
        this.itemId = itemId;
        this.itemIndex = itemIndex;
        this.itemInterfaceId = itemInterfaceId;
        this.gameObject = gameObject;
    }

    /**
     * @return The item identifier.
     */
    public int getItemId() {
        return itemId;
    }

    /**
     * @return The item index.
     */
    public int getItemIndex() {
        return itemIndex;
    }

    /**
     * @return The item interface identifier.
     */
    public int getItemInterfaceId() {
        return itemInterfaceId;
    }

    /**
     * @return The object identifier.
     */
    public int getObjectId() {
        return gameObject.getId();
    }

    /**
     * @return The object.
     */
    public GameObject getGameObject() {
        return gameObject;
    }
}
