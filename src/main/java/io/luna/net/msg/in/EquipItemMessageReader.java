package io.luna.net.msg.in;

import io.luna.game.event.impl.EquipItemEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data for when an item is equipped.
 *
 * @author lare96
 */
public final class EquipItemMessageReader extends GameMessageReader<EquipItemEvent> {

    @Override
    public EquipItemEvent decode(Player player, GameMessage msg) {
        int interfaceId = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        int itemId = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        int index = msg.getPayload().getShort(false, ValueType.ADD);
        return new EquipItemEvent(player, index, itemId, interfaceId);
    }

    @Override
    public boolean validate(Player player, EquipItemEvent event) {
        if (event.getItemId() <= 0 ||
                event.getIndex() < 0 ||
                event.getInterfaceId() <= 0) {
            return false;
        }
        return player.getInventory().contains(event.getIndex(), event.getItemId());
    }
}
