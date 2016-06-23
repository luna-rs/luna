package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.InboundMessageReader;

import static com.google.common.base.Preconditions.checkState;

/**
 * An {@link InboundMessageReader} implementation that decodes data sent when a {@link Player} attempts to rearrange items.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class SwapItemsMessageReader extends InboundMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int interfaceId = msg.getPayload().getShort(ByteTransform.A, ByteOrder.LITTLE);
        int insertionMode = msg.getPayload().get(ByteTransform.C);
        int fromIndex = msg.getPayload().getShort(ByteTransform.A, ByteOrder.LITTLE);
        int toIndex = msg.getPayload().getShort(ByteOrder.LITTLE);

        checkState(interfaceId > 0, "interfaceId <= 0");
        checkState(insertionMode == 0 || insertionMode == 1, "insertionMode != 0 or 1");
        checkState(fromIndex >= 0, "fromIndex < 0");
        checkState(toIndex >= 0, "toIndex < 0");

        boolean insertMode = (insertionMode == 1); // 0 = swap, 1 = insert

        switch (interfaceId) {
        case 3214:
            player.getInventory().swap(insertMode, fromIndex, toIndex);
            break;
        case 5382:
            player.getBank().swap(insertMode, fromIndex, toIndex);
            break;
        }
        return null;
    }
}