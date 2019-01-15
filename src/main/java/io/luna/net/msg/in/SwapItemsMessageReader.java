package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.model.item.ItemContainer;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when rearranging items.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SwapItemsMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int interfaceId = msg.getPayload().getShort(ValueType.ADD, ByteOrder.LITTLE);
        int insertionMode = msg.getPayload().get(ValueType.NEGATE);
        int fromIndex = msg.getPayload().getShort(ValueType.ADD, ByteOrder.LITTLE);
        int toIndex = msg.getPayload().getShort(ByteOrder.LITTLE);

        checkState(interfaceId > 0, "interfaceId <= 0");
        checkState(insertionMode == 0 || insertionMode == 1, "insertionMode != 0 or 1");
        checkState(fromIndex >= 0, "fromIndex < 0");
        checkState(toIndex >= 0, "toIndex < 0");

        boolean insertMode = (insertionMode == 1); // 0 = swap, 1 = insert
        Optional<ItemContainer> itemContainer = Optional.empty();

        switch (interfaceId) {
        case 3214:
            itemContainer = Optional.of(player.getInventory());
            break;
        case 5382:
            if(player.getBank().isOpen()) {
                itemContainer = Optional.of(player.getBank());
            }
            break;
        }

        itemContainer.ifPresent(items -> {
            if (insertMode) {
                items.insert(fromIndex, toIndex);
            } else {
                items.swap(fromIndex, toIndex);
            }
        });
        return null;
    }
}