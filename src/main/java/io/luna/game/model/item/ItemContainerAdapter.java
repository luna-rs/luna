package io.luna.game.model.item;

import io.luna.game.model.mobile.Player;
import io.luna.net.msg.out.SendGameInfoMessage;
import io.luna.net.msg.out.SendWidgetItemGroupMessage;

/**
 * @author lare96 <http://github.org/lare96>
 */
public abstract class ItemContainerAdapter implements ItemCollectionListener {

    private final Player player;

    public ItemContainerAdapter(Player player) {
        this.player = player;
    }

    @Override
    public void itemsAdded(ItemContainer collection) {
        sendItemsToWidget(collection);
    }

    @Override
    public void itemsRemoved(ItemContainer collection) {
        sendItemsToWidget(collection);
    }

    @Override
    public void capacityExceeded(ItemContainer collection) {
        player.queue(new SendGameInfoMessage(getCapacityExceededMsg()));
    }

    private void sendItemsToWidget(ItemContainer collection) {
        player.queue(new SendWidgetItemGroupMessage(getWidgetId(), collection.getItems()));
    }

    public abstract int getWidgetId();

    public abstract String getCapacityExceededMsg();
}
