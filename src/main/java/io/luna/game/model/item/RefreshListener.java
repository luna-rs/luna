package io.luna.game.model.item;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.WidgetIndexedItemsMessageWriter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * A listener that refreshes items when updates occur and displays capacity exceeded messages.
 *
 * @author lare96 <http://github.org/lare96>
 */
final class RefreshListener implements ItemContainerListener {

    /**
     * The player.
     */
    private final Player player;

    /**
     * A queue of refresh updates.
     */
    private final List<IndexedItem> refreshUpdates = new ArrayList<>();

    /**
     * The message displayed when the capacity has been exceeded.
     */
    private final String capacityMessage;

    /**
     * Creates a new {@link RefreshListener}.
     *
     * @param player The player.
     */
    public RefreshListener(Player player, String capacityMessage) {
        this.player = player;
        this.capacityMessage = capacityMessage;
    }

    @Override
    public final void onSingleUpdate(int index, ItemContainer items, Optional<Item> oldItem, Optional<Item> newItem) {
        Collection<IndexedItem> updateItem = ImmutableList.of(getItem(index, newItem));
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

    @Override
    public final void onCapacityExceeded(ItemContainer items) {
        player.sendMessage(capacityMessage);
    }

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
     * Sends a display update message to the primary and secondary widgets.
     *
     * @param items The underlying container.
     * @param updateItems The items to update.
     */
    private void sendMsg(ItemContainer items, Collection<IndexedItem> updateItems) {
        player.queue(new WidgetIndexedItemsMessageWriter(items.getPrimaryRefresh(), updateItems));

        if (items.getSecondaryRefresh().isPresent()) {
            int secondaryRefresh = items.getSecondaryRefresh().getAsInt();
            player.queue(new WidgetIndexedItemsMessageWriter(secondaryRefresh, updateItems));
        }
    }
}
