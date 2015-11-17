package io.luna.net.msg.in;

import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.InboundGameMessage;
import plugin.PickupItemEvent;

import static com.google.common.base.Preconditions.checkState;

/**
 * An {@link InboundGameMessage} implementation that decodes data that allows a {@link Player} to pickup an item off the
 * ground.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ReceivePickupItemMessage extends InboundGameMessage {

    @Override
    public Object readMessage(Player player, GameMessage msg) throws Exception {
        int y = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        int id = msg.getPayload().getShort(false);
        int x = msg.getPayload().getShort(false, ByteOrder.LITTLE);

        checkState(id < 0 || id >= ItemDefinition.DEFINITIONS.length, "invalid item id");
        return new PickupItemEvent(x, y, id);
    }
}
