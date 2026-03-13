package io.luna.net.msg.in;

import io.luna.game.event.impl.VoidEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} that intercepts data for mouse clicks when a player's account is flagged as suspicious.
 *
 * @author lare96
 */
public final class MouseFlaggedMessageReader extends GameMessageReader<VoidEvent> {

    @Override
    public VoidEvent decode(Player player, GameMessage msg) {
        return VoidEvent.INSTANCE;
    }
}
