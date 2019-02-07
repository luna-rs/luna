package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

import java.util.Map;

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

    /**
     * Determines if {@code item1} and {@code item2} are equal to {@code usedId} and {@code targetId}, and
     * vice-versa.
     *
     * @param item1 The first item.
     * @param item2 The second item.
     * @return {@code true} if the values are equal to the items used.
     */
    public boolean matches(int item1, int item2) {
        return item1 == usedId && item2 == targetId || item1 == targetId && item2 == usedId;
    }

    /**
     * Determines if {@code item} is equal to {@code usedId} or {@code targetId}.
     *
     * @param item The item.
     * @return {@code true} if {@code item} is equal to any of the items used.
     */
    public boolean matches(int item) {
        return item == usedId || item == targetId;
    }

    // TODO document and use in plugins
    public <V> V lookup(Map<Integer, V> from) {
        V value = from.get(usedId);
        if (value != null) {
            return value;
        }
        return from.get(targetId);
    }

    /**
     * @return The used item identifier.
     */
    public int getUsedId() {
        return usedId;
    }

    /**
     * @return The target item identifier.
     */
    public int getTargetId() {
        return targetId;
    }

    /**
     * @return The used item index.
     */
    public int getUsedIndex() {
        return usedIndex;
    }

    /**
     * @return The target item index.
     */
    public int getTargetIndex() {
        return targetIndex;
    }

    /**
     * @return The used interface identifier.
     */
    public int getUsedInterfaceId() {
        return usedInterfaceId;
    }

    /**
     * @return The target interface identifier.
     */
    public int getTargetInterfaceId() {
        return targetInterfaceId;
    }
}
