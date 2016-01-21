package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.OutboundGameMessage;

/**
 * An {@link OutboundGameMessage} implementation that disposes the login session.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SendLogoutMessage extends OutboundGameMessage {

    @Override
    public ByteMessage writeMessage(Player player) {
        return ByteMessage.message(109);
    }
}
