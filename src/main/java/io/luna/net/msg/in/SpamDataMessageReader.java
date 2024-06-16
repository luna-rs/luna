package io.luna.net.msg.in;

import io.luna.game.event.impl.NullEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts spam data.
 *
 * @author lare96
 */
public final class SpamDataMessageReader extends GameMessageReader<NullEvent> {

    @Override
    public NullEvent decode(Player player, GameMessage msg) {
        byte[] spamData = msg.getPayload().getBytes(msg.getSize());
        return NullEvent.INSTANCE;
    }
}
