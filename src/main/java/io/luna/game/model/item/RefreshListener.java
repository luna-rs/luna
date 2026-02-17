package io.luna.game.model.item;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.overlay.StandardInterface;
import io.luna.net.msg.out.WidgetIndexedItemsMessageWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * A specialized {@link ItemContainerListener} that translates container mutations into client-side widget refresh
 * messages.
 * <p>
 * This listener tracks which slots were modified during bulk operations and emits one or more
 * {@link WidgetIndexedItemsMessageWriter} instances describing those changes.
 * <p>
 * Updates may be sent for:
 * <ul>
 *     <li><b>Primary refresh</b>: the container's primary widget target.</li>
 *     <li><b>Secondary refresh</b>: an optional additional widget target, if configured.</li>
 * </ul>
 *
 * @author lare96
 */
public abstract class RefreshListener implements ItemContainerListener {

    /**
     * A {@link RefreshListener} implementation dedicated to updating the display for a single {@link Player}.
     * <p>
     * This listener optionally guards against sending updates when a specific {@link StandardInterface} is not open,
     * and provides a simple message when the container reports a capacity exceeded condition.
     */
    public static final class PlayerRefreshListener extends RefreshListener {

        /**
         * The player that receives refresh messages.
         */
        private final Player player;

        /**
         * Optional open interface constraint for whether refresh messages should be sent.
         */
        private final StandardInterface widget;

        /**
         * The message sent when the container reports capacity exceeded.
         */
        private final String capacityMessage;

        /**
         * Creates a new {@link PlayerRefreshListener}.
         *
         * @param player The player receiving refresh messages.
         * @param widget Optional open interface constraint; if {@code null}, updates are always sent.
         * @param capacityMessage The message sent when capacity is exceeded.
         */
        public PlayerRefreshListener(Player player, StandardInterface widget, String capacityMessage) {
            this.player = player;
            this.widget = widget;
            this.capacityMessage = capacityMessage;
        }

        /**
         * Creates a new {@link PlayerRefreshListener} that always sends updates regardless of interface state.
         *
         * @param player The player receiving refresh messages.
         * @param capacityMessage The message sent when capacity is exceeded.
         */
        public PlayerRefreshListener(Player player, String capacityMessage) {
            this(player, null, capacityMessage);
        }

        @Override
        public void displayUpdate(ItemContainer items, List<IndexedItem> updateItems,
                                  WidgetIndexedItemsMessageWriter msg) {
            if (widget == null || widget.isOpen()) {
                player.queue(msg);
            }
        }

        @Override
        public void onCapacityExceeded(ItemContainer items) {
            player.sendMessage(capacityMessage);
        }
    }

    /**
     * Accumulated slot updates during a bulk operation.
     */
    private final List<IndexedItem> refreshUpdates = new ArrayList<>();

    @Override
    public final void onSingleUpdate(int index, ItemContainer items, Item oldItem, Item newItem) {
        List<IndexedItem> updateItem = List.of(getItem(index, newItem));
        sendUpdates(items, updateItem);
    }

    @Override
    public final void onBulkUpdate(int index, ItemContainer items, Item oldItem, Item newItem) {
        refreshUpdates.add(getItem(index, newItem));
    }

    @Override
    public final void onBulkUpdateCompleted(ItemContainer items) {
        sendUpdates(items, refreshUpdates);
        refreshUpdates.clear();
    }

    /**
     * Invoked when a display update message has been created and needs to be delivered to its intended recipient(s).
     * <p>
     * Implementations decide how to send the message (queue to a player, broadcast to viewers, etc.).
     *
     * @param items The item container that produced the update.
     * @param updateItems The set of updated slot values included in the message.
     * @param msg The display update message to deliver.
     */
    public abstract void displayUpdate(ItemContainer items, List<IndexedItem> updateItems,
                                       WidgetIndexedItemsMessageWriter msg);

    /**
     * Converts an optional item into an {@link IndexedItem} representation for a given slot index.
     * <p>
     * If the item is absent, a sentinel {@link IndexedItem} is created using {@code id = -1} and {@code amount = 0}
     * to represent an empty slot on the client.
     *
     * @param index The container slot index.
     * @param item The optional item value.
     * @return An indexed item representing the slot state.
     */
    private IndexedItem getItem(int index, Item item) {
        return item != null ? new IndexedItem(index, item) : new IndexedItem(index, -1, 0);
    }

    /**
     * Constructs and forwards refresh messages for the given set of updated items.
     * <p>
     * Messages are always created for the container's primary refresh target. If the container has a secondary
     * refresh target configured, a second message is also created and forwarded.
     *
     * @param items The underlying container.
     * @param updateItems The items to create messages for.
     */
    private void sendUpdates(ItemContainer items, List<IndexedItem> updateItems) {
        displayUpdate(items, updateItems, new WidgetIndexedItemsMessageWriter(items.getPrimaryWidget(), updateItems));

        OptionalInt secondaryRefresh = items.getSecondaryWidget();
        if (secondaryRefresh.isPresent()) {
            int id = secondaryRefresh.getAsInt();
            displayUpdate(items, updateItems,
                    new WidgetIndexedItemsMessageWriter(id, updateItems));
        }
    }
}
