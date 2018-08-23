package io.luna.game.model.item;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.GameChatboxMessageWriter;
import io.luna.net.msg.out.WidgetItemMessageWriter;

import java.util.Optional;

/**
 * An adapter for item container listeners.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class ItemContainerAdapter implements ItemContainerListener {

    /**
     * The player.
     */
    private final Player player;

    /**
     * Creates a new {@link ItemContainerAdapter}.
     *
     * @param player The player.
     */
    public ItemContainerAdapter(Player player) {
        this.player = player;
    }

    /**
     * Will send a single item widget update.
     */
    @Override
    public void onSingleUpdate(ItemContainer items, Optional<Item> oldItem, Optional<Item> newItem, int index) {
        sendItem(newItem.orElse(null), index);
    }

    /**
     * This implementation does nothing.
     */
    @Override
    public void onBulkUpdate(ItemContainer items, Optional<Item> oldItem, Optional<Item> newItem, int index) {
    }

    /**
     * Will send a group item widget update.
     */
    @Override
    public void onBulkUpdateCompleted(ItemContainer items) {
        sendItemGroup(items);
    }

    /**
     * Will send a game message with the capacity exceeded text.
     */
    @Override
    public void onCapacityExceeded(ItemContainer items) {
        player.queue(new GameChatboxMessageWriter(getCapacityExceededMsg()));
    }

    /**
     * Displays a group of items on widget {@code getWidgetId()}.
     */
    protected void sendItemGroup(ItemContainer container) {
        player.queue(container.constructRefresh(getWidgetId()));
    }

    /**
     * Displays a single item on widget {@code getWidgetId()} at {@code index}.
     */
    protected void sendItem(Item item, int index) {
        player.queue(new WidgetItemMessageWriter(getWidgetId(), index, item));
    }

    /**
     * Returns the widget to display items on.
     */
    public abstract int getWidgetId();

    /**
     * Returns the message sent when the capacity is exceeded.
     */
    public abstract String getCapacityExceededMsg();
}
