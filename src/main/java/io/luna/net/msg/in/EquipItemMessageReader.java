package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.item.EquipItemEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link GameMessageReader} implementation that intercepts data for when an item is equipped.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EquipItemMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) {
        int itemId = msg.getPayload().getShort(false);
        int index = msg.getPayload().getShort(false, ValueType.ADD);
        int interfaceId = msg.getPayload().getShort(false, ValueType.ADD);

        checkState(itemId > 0, "itemId <= 0");
        checkState(index >= 0, "index < 0");
        checkState(interfaceId > 0, "interfaceId <= 0");

        var inventory = player.getInventory();
        
        if (inventory.computeIdForIndex(index).orElse(-1) != itemId) {
            return null;
        }
        
        return new EquipItemEvent(player, index, itemId, interfaceId);
    }
}
