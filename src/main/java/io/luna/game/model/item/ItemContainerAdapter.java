package io.luna.game.model.item;

import io.luna.game.model.mobile.Player;
import io.luna.net.msg.out.GameChatboxMessageWriter;

/**
 * An adapter for {@link ItemContainerListener} that updates {@link Item}s on a widget whenever items change, and sends the
 * underlying {@link Player} a message when the container is full.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class ItemContainerAdapter implements ItemContainerListener {

    /**
     * The {@link Player} instance.
     */
    private final Player player;

    /**
     * Creates a new {@link ItemContainerAdapter}.
     *
     * @param player The {@link Player} instance.
     */
    public ItemContainerAdapter(Player player) {
        this.player = player;
    }

    @Override
    public void itemUpdated(ItemContainer container, int index) {
        sendItemsToWidget(container);
    }

    @Override
    public void bulkItemsUpdated(ItemContainer container) {
        sendItemsToWidget(container);
    }

    @Override
    public void capacityExceeded(ItemContainer container) {
        player.queue(new GameChatboxMessageWriter(getCapacityExceededMsg()));
    }

    /**
     * Queues a message that displays items from an {@link ItemContainer} on a widget.
     */
    private void sendItemsToWidget(ItemContainer container) {
        player.queue(container.constructRefresh(getWidgetId()));
    }

    /**
     * @return The widget to display items on.
     */
    public abstract int getWidgetId();

    /**
     * @return The message sent when the {@link ItemContainer} exceeds its capacity.
     */
    public abstract String getCapacityExceededMsg();
}
