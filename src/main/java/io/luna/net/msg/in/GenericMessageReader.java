package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.model.mobile.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.InboundMessageReader;

/**
 * An {@link InboundMessageReader} implementation that serves as the default message handler. It does nothing when executed.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class GenericMessageReader extends InboundMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        return null;
    }
}
