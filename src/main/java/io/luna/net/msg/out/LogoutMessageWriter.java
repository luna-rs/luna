package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.OutboundMessageWriter;

/**
 * An {@link OutboundMessageWriter} implementation that disposes the login session.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class LogoutMessageWriter extends OutboundMessageWriter {

    @Override
    public ByteMessage write(Player player) {
        return ByteMessage.message(109);
    }
}
