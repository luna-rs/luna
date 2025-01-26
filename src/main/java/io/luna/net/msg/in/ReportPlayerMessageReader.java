package io.luna.net.msg.in;

import io.luna.game.event.impl.NullEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when the player reports another player.
 *
 * @author lare96
 */
public final class ReportPlayerMessageReader extends GameMessageReader<NullEvent> {

    @Override
    public NullEvent decode(Player player, GameMessage msg) {
        // TODO https://github.com/luna-rs/luna/issues/87
        return NullEvent.INSTANCE;
    }
}
