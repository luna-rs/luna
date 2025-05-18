package io.luna.net.msg.in;

import io.luna.game.event.impl.NullEvent;
import io.luna.game.model.item.ItemContainer;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when rearranging items.
 *
 * @author lare96
 */
public final class ArrangeBankMessageReader extends GameMessageReader<NullEvent> {

    @Override
    public NullEvent decode(Player player, GameMessage msg) {
        int toIndex = msg.getPayload().getShort(ByteOrder.LITTLE, ValueType.ADD);
        int insertionMode = msg.getPayload().get(false, ValueType.ADD);
        int interfaceId = msg.getPayload().getShort(ValueType.ADD);
        int fromIndex = msg.getPayload().getShort(ByteOrder.LITTLE);

        boolean mode = (insertionMode == 1); // 0 = swap, 1 = insert
        ItemContainer itemContainer = null;
        if (interfaceId == 3214) {
            itemContainer = player.getInventory();
        } else if (interfaceId == 5382) {
            itemContainer = player.getBank();
        }
        if (itemContainer != null) {//arrange
            if (mode) {
                itemContainer.insert(fromIndex, toIndex);
            } else {
                itemContainer.swap(toIndex, fromIndex);
            }
        }
        return NullEvent.INSTANCE;
    }
}