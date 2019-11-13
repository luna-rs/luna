package io.luna.game.model.item;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.WidgetIndexedItemsMessageWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A listener that tracks indexes that need to be refreshed and forwards the resulting display update
 * messages to listener functions.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class RefreshListener implements ItemContainerListener {

    /**
     * A refresh listener dedicated to updating the display for a single Player. Will also send capacity
     * exceeded failure messages.
     */
    public static final class PlayerRefreshListener extends RefreshListener {

        /**
         * The player.
         */
        private final Player player;


        /**
         * The message displayed when the capacity has been exceeded.
         */
        private final String capacityMessage;

        /**
         * Creates a new {@link RefreshListener}.
         *
         * @param player The player.
         * @param capacityMessage The message sent when capacity is exceeded.
         */
        public PlayerRefreshListener(Player player, String capacityMessage) {
            this.player = player;
            this.capacityMessage = capacityMessage;
        }

        @Override
        public void displayUpdate(ItemContainer items, List<IndexedItem> updateItems,
                                  WidgetIndexedItemsMessageWriter msg) {
            player.queue(msg);
        }

        @Override
        public void onCapacityExceeded(ItemContainer items) {
            player.sendMessage(capacityMessage);
        }
    }

    /**
     * A queue of refresh updates.
     */
    private final List<IndexedItem> refreshUpdates = new ArrayList<>();

    @Override
    public final void onSingleUpdate(int index, ItemContainer items, Optional<Item> oldItem, Optional<Item> newItem) {
        List<IndexedItem> updateItem = List.of(getItem(index, newItem));
        sendMsg(items, updateItem);
    }

    @Override
    public final void onBulkUpdate(int index, ItemContainer items, Optional<Item> oldItem, Optional<Item> newItem) {
        refreshUpdates.add(getItem(index, newItem));
    }

    @Override
    public final void onBulkUpdateCompleted(ItemContainer items) {
        sendMsg(items, refreshUpdates);
        refreshUpdates.clear();
    }

    /**
     * A function invoked when a display update message has been created and needs to be sent.
     *
     * @param items The item container.
     * @param updateItems The items that the message was created for.
     * @param msg The display update message.
     */
    public abstract void displayUpdate(ItemContainer items, List<IndexedItem> updateItems,
                                       WidgetIndexedItemsMessageWriter msg);

    /**
     * Converts {@code item} into an item with an index.
     *
     * @param index The index.
     * @param item The item to convert.
     * @return The indexed item.
     */
    private IndexedItem getItem(int index, Optional<Item> item) {
        return item.map(it -> new IndexedItem(index, it)).
                orElse(new IndexedItem(index, -1, 0));
    }

    /**
     * Forwards display update message instances to listener function.
     *
     * @param items The underlying container.
     * @param updateItems The items to create messages for.
     */
    private void sendMsg(ItemContainer items, List<IndexedItem> updateItems) {
        displayUpdate(items, updateItems,
                new WidgetIndexedItemsMessageWriter(items.getPrimaryRefresh(), updateItems));

        OptionalInt secondaryRefresh = items.getSecondaryRefresh();
        if (secondaryRefresh.isPresent()) {
            int id = secondaryRefresh.getAsInt();
            displayUpdate(items, updateItems,
                    new WidgetIndexedItemsMessageWriter(id, updateItems));
        }
    }
}
