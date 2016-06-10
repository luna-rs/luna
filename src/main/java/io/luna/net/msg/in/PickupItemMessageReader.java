package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.PickupItemEvent;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.InboundMessageReader;

import static com.google.common.base.Preconditions.checkState;

/**
 * An {@link InboundMessageReader} implementation that decodes data sent when a {@link Player} tries to pick up an item.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PickupItemMessageReader extends InboundMessageReader {

    @Override
    public Event decode(Player player, GameMessage msg) throws Exception {
        int y = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        int id = msg.getPayload().getShort(false);
        int x = msg.getPayload().getShort(false, ByteOrder.LITTLE);

        checkState(id < 0 || id >= ItemDefinition.DEFINITIONS.length, "invalid item id");
        return new PickupItemEvent(x, y, id);
    }
}
