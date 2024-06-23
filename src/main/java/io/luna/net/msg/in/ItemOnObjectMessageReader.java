package io.luna.net.msg.in;

import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent;
import io.luna.game.model.Position;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when an item is used on an object.
 *
 * @author lare96
 */
public final class ItemOnObjectMessageReader extends GameMessageReader<ItemOnObjectEvent> {

    @Override
    public ItemOnObjectEvent decode(Player player, GameMessage msg) {
        int objectId = msg.getPayload().getShort(true, ByteOrder.LITTLE);
        int itemInterfaceId = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        int itemId = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        int objectY = msg.getPayload().getShort(true, ByteOrder.LITTLE);
        int itemIndexId = msg.getPayload().getShort(true);
        int objectX = msg.getPayload().getShort(true, ByteOrder.LITTLE, ValueType.ADD);

        Position objectPosition = new Position(objectX, objectY, player.getPosition().getZ());
        GameObject gameObject = player.getWorld().getObjects().findAll(objectPosition).
                filter(nextObject -> nextObject.getId() == objectId &&
                        nextObject.getPosition().equals(objectPosition) &&
                        nextObject.isVisibleTo(player)).findFirst().orElse(null);

        return new ItemOnObjectEvent(player, itemId, itemIndexId, itemInterfaceId, gameObject);
    }

    @Override
    public boolean validate(Player player, ItemOnObjectEvent event) {
        if (event.getUsedItemInterface() < 1 ||
                event.getUsedItemIndex() < 0 ||
                !ItemDefinition.isIdValid(event.getUsedItemId()) ||
                event.getGameObject() == null) {
            return false;
        }

        if (event.getUsedItemInterface() == 3214) {
            return player.getInventory().contains(event.getUsedItemIndex(), event.getUsedItemId());
        }
        return false;
    }
}
