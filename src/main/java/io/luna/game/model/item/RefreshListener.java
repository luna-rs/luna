package io.luna.game.model.item;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.WidgetIndexedItemGroupMessageWriter;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;

/**
 * An adapter for item container listeners.
 *
 * @author lare96 <http://github.org/lare96>
 */
final class RefreshListener implements ItemContainerListener {
// Change name
    /**
     * The player.
     */
    private final Player player;

    /**
     *
     */
    private final Queue<IndexedItem> bulkUpdates = new ArrayDeque<>();
    String capacityExceededMsg;

    /**
     * Creates a new {@link DefaultRefreshListener}.
     *
     * @param player The player.
     */
    public RefreshListener(Player player, String capacityExceededMsg) {
        this.player = player;
    }

    /**
     * Will send a single item widget update.
     */
    @Override
    public final void onSingleUpdate(int index, ItemContainer items, Optional<Item> oldItem, Optional<Item> newItem) {
        IndexedItem updateItem = newItem.map(it -> new IndexedItem(index, it)).
                orElse(new IndexedItem(index, -1, 0));

        sendMsg(items.primaryRefreshId, updateItem);
        items.secondaryRefreshId.ifPresent(id -> sendMsg(id, updateItem));
    }

    @Override
    public final void onBulkUpdate(int index, Optional<Item> oldItem, Optional<Item> newItem, ItemContainer items) {
        int id = newItem.map(Item::getId).orElse(-1);
        int amount = newItem.map(Item::getAmount).orElse(0);
        bulkUpdates.add(new IndexedItem(index, id, amount));
    }

    /**
     * Will send a group item widget update.
     */
    @Override
    public final void onBulkUpdateCompleted(ItemContainer items) {
        sendMsg(items.primaryRefreshId, bulkUpdates);
        items.secondaryRefreshId.ifPresent(id -> sendMsg(id, bulkUpdates));
        bulkUpdates.clear();
    }

    /**
     * Will send a game message with the capacity exceeded text.
     */
    @Override
    public final void onCapacityExceeded(ItemContainer items) {
        player.sendMessage(capacityExceededMsg);
    }


    private void sendMsg(int id, IndexedItem item) {
        player.queue(new WidgetIndexedItemGroupMessageWriter(id, item));
    }

    private void sendMsg(int id, Iterable<? extends IndexedItem> item) {
        player.queue(new WidgetIndexedItemGroupMessageWriter(id, item));
    }
}
