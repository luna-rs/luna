package io.luna.game.event.impl;

import io.luna.game.event.Event;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkState;

/**
 * An event implementation sent when a player uses an item on another item.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemOnItemEvent extends Event {

    /**
     * The identifier of the item used.
     */
    private final int usedId;

    /**
     * The identifier of the target item.
     */
    private final int targetId;

    /**
     * The index of the item used.
     */
    private final int usedIndex;

    /**
     * The index of the target item.
     */
    private final int targetIndex;

    /**
     * The identifier for the interface the item used is on.
     */
    private final int usedInterfaceId;

    /**
     * The identifier for the interface the target item is on.
     */
    private final int targetInterfaceId;

    /**
     * Creates a new {@link ItemOnItemEvent}.
     *
     * @param usedId The identifier of the item used.
     * @param targetId The identifier of the target item.
     * @param usedIndex The index of the item used.
     * @param targetIndex The index of the target item.
     * @param usedInterfaceId The identifier for the interface the item used is on.
     * @param targetInterfaceId The identifier for the interface the target item is on.
     */
    public ItemOnItemEvent(int usedId, int targetId, int usedIndex, int targetIndex, int usedInterfaceId,
        int targetInterfaceId) {
        this.usedId = usedId;
        this.targetId = targetId;
        this.usedIndex = usedIndex;
        this.targetIndex = targetIndex;
        this.usedInterfaceId = usedInterfaceId;
        this.targetInterfaceId = targetInterfaceId;
    }

    @Override
    public boolean matches(Object... args) {
        checkState(args.length == 2, "args.length != 1");
        return Objects.equals(args[0], usedId) && Objects.equals(args[1], targetId);
    }

    /**
     * @return The identifier of the item used.
     */
    public int getUsedId() {
        return usedId;
    }

    /**
     * @return The identifier of the target item.
     */
    public int getTargetId() {
        return targetId;
    }

    /**
     * @return The index of the item used.
     */
    public int getUsedIndex() {
        return usedIndex;
    }

    /**
     * @return The index of the target item.
     */
    public int getTargetIndex() {
        return targetIndex;
    }

    /**
     * @return The identifier for the interface the item used is on.
     */
    public int getUsedInterfaceId() {
        return usedInterfaceId;
    }

    /**
     * @return The identifier for the interface the target item is on.
     */
    public int getTargetInterfaceId() {
        return targetInterfaceId;
    }
}
