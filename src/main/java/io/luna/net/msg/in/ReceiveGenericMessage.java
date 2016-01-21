package io.luna.net.msg.in;

import io.luna.game.model.mobile.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.InboundGameMessage;

/**
 * An {@link InboundGameMessage} implementation that serves as the default message handler. It does nothing when executed.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ReceiveGenericMessage extends InboundGameMessage {

    @Override
    public Object readMessage(Player player, GameMessage msg) throws Exception {
        return null;
    }
}
