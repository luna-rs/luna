package io.luna.net.msg.in;

import io.luna.game.event.impl.DropItemEvent;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data for when an item is dropped.
 *
 * @author lare96
 */
public final class DropItemMessageReader extends GameMessageReader<DropItemEvent> {


    @Override
    public DropItemEvent decode(Player player, GameMessage msg) {
        int index = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        int itemId = msg.getPayload().getShort(false, ByteOrder.LITTLE, ValueType.ADD);
        int widgetId = msg.getPayload().getShort(false, ByteOrder.LITTLE, ValueType.ADD);
        return new DropItemEvent(player, itemId, widgetId, index);
    }

    @Override
    public boolean validate(Player player, DropItemEvent event) {
        int itemId = event.getItemId();
        int index = event.getIndex();

        if (event.getWidgetId() != 3214 || // Click didn't come from inventory.
                !ItemDefinition.isIdValid(itemId) || // Item ID invalid.
                event.getIndex() < 0 || // Index < 0.
                event.getIndex() >= player.getInventory().capacity()) { // Index exceeds inventory capacity.
            return false;
        }

        // Check if inventory item ID is equal to event item ID.
        return player.getInventory().contains(index, itemId);
    }
}