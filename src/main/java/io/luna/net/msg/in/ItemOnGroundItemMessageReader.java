package io.luna.net.msg.in;

import io.luna.game.event.impl.UseItemEvent.ItemOnGroundItemEvent;
import io.luna.game.model.Position;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when an item is used on a ground item.
 *
 * @author lare96
 */
public final class ItemOnGroundItemMessageReader extends GameMessageReader<ItemOnGroundItemEvent> {

    @Override
    public ItemOnGroundItemEvent decode(Player player, GameMessage msg) {
        int usedIndex = msg.getPayload().getShort(false, ByteOrder.LITTLE, ValueType.ADD);
        int usedItemId = msg.getPayload().getShort(false, ValueType.ADD);

        int groundItemY = msg.getPayload().getShort(false, ByteOrder.LITTLE, ValueType.ADD);
        int groundItemX = msg.getPayload().getShort(false, ByteOrder.LITTLE, ValueType.ADD);

        int usedInterfaceId = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        int groundItemId = msg.getPayload().getShort(false, ByteOrder.LITTLE);

        Position itemPosition = new Position(groundItemX, groundItemY, player.getPosition().getZ());
        GroundItem groundItem = player.getWorld().getItems().findAll(itemPosition).
                filter(item -> item.getId() == groundItemId &&
                        item.getPosition().equals(itemPosition) &&
                        item.isVisibleTo(player)).findFirst().orElse(null);

        return new ItemOnGroundItemEvent(player, usedItemId, usedIndex, usedInterfaceId, groundItem);
    }

    @Override
    public boolean validate(Player player, ItemOnGroundItemEvent event) {
        if (event.getUsedItemInterface() < 1 ||
                event.getUsedItemIndex() < 0 ||
                !ItemDefinition.isIdValid(event.getUsedItemId()) ||
                event.getGroundItem() == null) {
            return false;
        }

        if (event.getUsedItemInterface() == 3214) {
            return player.getInventory().contains(event.getUsedItemIndex(), event.getUsedItemId());
        }
        return false;
    }
}
