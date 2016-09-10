package io.luna.game.event.impl;

import io.luna.game.event.EventArguments;
import io.luna.game.model.mobile.Player;

/**
 * An event sent when a player uses an item on another item.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemOnItemEvent extends PlayerEvent {

    /**
     * The used item identifier.
     */
    private final int usedId;

    /**
     * The target item identifier.
     */
    private final int targetId;

    /**
     * The used item index.
     */
    private final int usedIndex;

    /**
     * The target item index.
     */
    private final int targetIndex;

    /**
     * The used interface identifier.
     */
    private final int usedInterfaceId;

    /**
     * The target interface identifier.
     */
    private final int targetInterfaceId;

    /**
     * Creates a new {@link ItemOnItemEvent}.
     *
     * @param player The player.
     * @param usedId The used item identifier.
     * @param targetId The target item identifier.
     * @param usedIndex The used item index.
     * @param targetIndex The target item index.
     * @param usedInterfaceId The used interface identifier.
     * @param targetInterfaceId The target interface identifier.
     */
    public ItemOnItemEvent(Player player, int usedId, int targetId, int usedIndex, int targetIndex,
        int usedInterfaceId, int targetInterfaceId) {
        super(player);
        this.usedId = usedId;
        this.targetId = targetId;
        this.usedIndex = usedIndex;
        this.targetIndex = targetIndex;
        this.usedInterfaceId = usedInterfaceId;
        this.targetInterfaceId = targetInterfaceId;
    }

    @Override
    public boolean matches(EventArguments args) {
        return args.equals(0, usedId) && args.equals(1, targetId) || args.equals(0, targetId) && args
            .equals(1, usedId);
    }

    /**
     * @return The used item identifier.
     */
    public int usedId() {
        return usedId;
    }

    /**
     * @return The target item identifier.
     */
    public int targetId() {
        return targetId;
    }

    /**
     * @return The used item index.
     */
    public int usedIndex() {
        return usedIndex;
    }

    /**
     * @return The target item index.
     */
    public int targetIndex() {
        return targetIndex;
    }

    /**
     * @return The used interface identifier.
     */
    public int usedInterfaceId() {
        return usedInterfaceId;
    }

    /**
     * @return The target interface identifier.
     */
    public int targetInterfaceId() {
        return targetInterfaceId;
    }
}
