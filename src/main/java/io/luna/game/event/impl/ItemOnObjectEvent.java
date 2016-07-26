package io.luna.game.event.impl;

import io.luna.game.event.Event;

/**
 * An {@link Event} implementation sent when a player uses an item on an object.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemOnObjectEvent extends Event {

    /**
     * The identifier for the item used.
     */
    private final int itemId;

    /**
     * The index of the item used.
     */
    private final int itemIndex;

    /**
     * The interface that the item used is on.
     */
    private final int itemInterfaceId;

    /**
     * The identifier for the target object.
     */
    private final int objectId;

    /**
     * The x coordinate of the target object.
     */
    private final int objectX;

    /**
     * The y coordinate of the target object.
     */
    private final int objectY;

    /**
     * Creates a new {@link ItemOnObjectEvent}.
     *
     * @param itemId The identifier for the item used.
     * @param itemIndex The index of the item used.
     * @param itemInterfaceId The interface that the item used is on.
     * @param objectId The identifier for the target object.
     * @param objectX The x coordinate of the target object.
     * @param objectY The y coordinate of the target object.
     */
    public ItemOnObjectEvent(int itemId, int itemIndex, int itemInterfaceId, int objectId, int objectX, int objectY) {
        this.itemId = itemId;
        this.itemIndex = itemIndex;
        this.itemInterfaceId = itemInterfaceId;
        this.objectId = objectId;
        this.objectX = objectX;
        this.objectY = objectY;
    }

    /**
     * @return The identifier for the item used.
     */
    public int getItemId() {
        return itemId;
    }

    /**
     * @return The index of the item used.
     */
    public int getItemIndex() {
        return itemIndex;
    }

    /**
     * @return The interface that the item used is on.
     */
    public int getItemInterfaceId() {
        return itemInterfaceId;
    }

    /**
     * @return The identifier for the target object.
     */
    public int getObjectId() {
        return objectId;
    }

    /**
     * @return The x coordinate of the target object.
     */
    public int getObjectX() {
        return objectX;
    }

    /**
     * @return The y coordinate of the target object.
     */
    public int getObjectY() {
        return objectY;
    }
}
