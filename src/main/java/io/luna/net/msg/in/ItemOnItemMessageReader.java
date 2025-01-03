package io.luna.net.msg.in;

import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when an item is used on another item.
 *
 * @author lare96
 */
public final class ItemOnItemMessageReader extends GameMessageReader<ItemOnItemEvent> {

    @Override
    public ItemOnItemEvent decode(Player player, GameMessage msg) {
        int targetId = msg.getPayload().getShort(false);
        int usedIndex = msg.getPayload().getShort(false, ByteOrder.LITTLE);

        int usedId = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        int targetInterfaceId = msg.getPayload().getShort(false, ByteOrder.LITTLE, ValueType.ADD);

        int targetIndex = msg.getPayload().getShort(false, ValueType.ADD);
        int usedInterfaceId = msg.getPayload().getShort(false, ValueType.ADD);
        return new ItemOnItemEvent(player, usedId, targetId, usedIndex, targetIndex, usedInterfaceId,
                targetInterfaceId);
    }

    @Override
    public boolean validate(Player player, ItemOnItemEvent event) {
        if (event.getTargetItemIndex() < 0 ||
                event.getUsedItemIndex() < 0 ||
                !ItemDefinition.isIdValid(event.getTargetItemId()) ||
                    !ItemDefinition.isIdValid(event.getUsedItemId()) ||
                event.getTargetItemInterface() < 1 ||
                event.getUsedItemInterface() < 1) {
            return false;
        }

        if (event.getUsedItemInterface() == 3214 && event.getTargetItemInterface() == 3214) {
            return player.getInventory().contains(event.getUsedItemIndex(), event.getUsedItemId()) &&
                    player.getInventory().contains(event.getTargetItemIndex(), event.getTargetItemId());
        }
        return false;
    }

    @Override
    public void handle(Player player, ItemOnItemEvent event)  {
        player.interruptAction();
    }
}
