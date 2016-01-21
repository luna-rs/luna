package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.OutboundGameMessage;

/**
 * An {@link OutboundGameMessage} implementation that sends a message containing the current region.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SendRegionMessage extends OutboundGameMessage {

    @Override
    public ByteMessage writeMessage(Player player) {
        ByteMessage msg = ByteMessage.message(73);
        msg.putShort(player.getPosition().getRegionX() + 6, ByteTransform.A);
        msg.putShort(player.getPosition().getRegionY() + 6);
        return msg;
    }
}
