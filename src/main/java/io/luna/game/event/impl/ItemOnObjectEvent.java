package io.luna.game.event.impl;

import io.luna.game.event.EventArguments;
import io.luna.game.model.mob.Player;

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
     * The object identifier.
     */
    private final int objectId;

    /**
     * The object's x coordinate.
     */
    private final int objectX;

    /**
     * The object's y coordinate.
     */
    private final int objectY;

    /**
     * Creates a new {@link ItemOnObjectEvent}.
     *
     * @param player The player.
     * @param itemId The item identifier.
     * @param itemIndex The item index.
     * @param itemInterfaceId The item interface identifier.
     * @param objectId The object identifier.
     * @param objectX The object's x coordinate.
     * @param objectY The object's y coordinate.
     */
    public ItemOnObjectEvent(Player player, int itemId, int itemIndex, int itemInterfaceId, int objectId,
        int objectX, int objectY) {
        super(player);
        this.itemId = itemId;
        this.itemIndex = itemIndex;
        this.itemInterfaceId = itemInterfaceId;
        this.objectId = objectId;
        this.objectX = objectX;
        this.objectY = objectY;
    }

    @Override
    public boolean matches(EventArguments args) {
        return args.equals(0, itemId) && args.equals(1, objectId);
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
        return objectId;
    }

    /**
     * @return The object's x coordinate.
     */
    public int getObjectX() {
        return objectX;
    }

    /**
     * @return The object's y coordinate.
     */
    public int getObjectY() {
        return objectY;
    }
}
