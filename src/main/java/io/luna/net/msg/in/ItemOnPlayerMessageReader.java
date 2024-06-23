package io.luna.net.msg.in;

import io.luna.game.event.impl.UseItemEvent.ItemOnPlayerEvent;
import io.luna.game.model.World;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} that intercepts data sent for when a player uses an item on a player.
 *
 * @author lare96
 */
public final class ItemOnPlayerMessageReader extends GameMessageReader<ItemOnPlayerEvent> {

    @Override
    public ItemOnPlayerEvent decode(Player player, GameMessage msg) {
        World world = player.getWorld();
        int itemId = msg.getPayload().getShort(ByteOrder.LITTLE);
        int itemIndex = msg.getPayload().getShort(ByteOrder.LITTLE, ValueType.ADD);
        int itemInterfaceId = msg.getPayload().getShort();
        int playerIndex = msg.getPayload().getShort(ValueType.ADD);
        return new ItemOnPlayerEvent(player, itemId, itemIndex, itemInterfaceId, world.getPlayers().get(playerIndex));
    }

    @Override
    public boolean validate(Player player, ItemOnPlayerEvent event) {
        if (event.getUsedItemInterface() < 1 ||
                event.getUsedItemIndex() < 0 ||
                !ItemDefinition.isIdValid(event.getUsedItemId()) ||
                event.getTargetPlayer() == null) {
            return false;
        }
        if (event.getUsedItemInterface() == 3214) {
            return player.getInventory().contains(event.getUsedItemIndex(), event.getUsedItemId());
        }
        return false;
    }
}
