package io.luna.net.msg.in;

import io.luna.game.event.impl.UseItemEvent.ItemOnNpcEvent;
import io.luna.game.model.World;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when an item is used on a npc.
 *
 * @author lare96
 */
public final class ItemOnNpcMessageReader extends GameMessageReader<ItemOnNpcEvent> {

    @Override
    public ItemOnNpcEvent decode(Player player, GameMessage msg) {
        World world = player.getWorld();
        int npcIndex = msg.getPayload().getShort();
        int itemId = msg.getPayload().getShort(ByteOrder.LITTLE);
        int itemInterfaceId = msg.getPayload().getShort(ByteOrder.LITTLE, ValueType.ADD);
        int itemIndex = msg.getPayload().getShort();
        return new ItemOnNpcEvent(player, itemId, itemIndex, itemInterfaceId, world.getNpcs().get(npcIndex));
    }

    @Override
    public boolean validate(Player player, ItemOnNpcEvent event) {
        if (event.getUsedItemInterface() < 1 ||
                event.getUsedItemIndex() < 0 ||
                !ItemDefinition.isIdValid(event.getUsedItemId()) ||
                event.getTargetNpc() == null) {
            return false;
        }
        if(event.getUsedItemInterface() == 3214) {
            return player.getInventory().contains(event.getUsedItemIndex(), event.getUsedItemId());
        }
        return false;
    }

    @Override
    public void handle(Player player, ItemOnNpcEvent event) {
        player.interruptAction();
    }
}
