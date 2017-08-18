package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.model.item.Inventory;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.MessageReader;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link MessageReader} implementation that intercepts data for when an item is equipped.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EquipItemMessageReader extends MessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int itemId = msg.getPayload().getShort(false);
        int index = msg.getPayload().getShort(false, ByteTransform.A);
        int interfaceId = msg.getPayload().getShort(false, ByteTransform.A);

        checkState(itemId > 0, "itemId <= 0");
        checkState(index >= 0, "index < 0");
        checkState(interfaceId > 0, "interfaceId <= 0");

        Inventory inventory = player.getInventory();
        if (inventory.computeIdForIndex(index).orElse(-1) != itemId) {
            return null;
        }

        player.interruptAction();
        player.getEquipment().equip(index);
        return null;
    }
}
