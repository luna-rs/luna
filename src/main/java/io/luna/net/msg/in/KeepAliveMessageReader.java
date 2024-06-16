package io.luna.net.msg.in;

import io.luna.game.event.impl.NullEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} that intercepts data sent to keep the connection alive.
 *
 * @author lare96
 */
public final class KeepAliveMessageReader extends GameMessageReader<NullEvent> {

    @Override
    public NullEvent decode(Player player, GameMessage msg) {
        return NullEvent.INSTANCE;
    }
}
