package io.luna.net.msg.in;

import io.luna.game.event.impl.CloseInterfaceEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data for when a close button
 * on an interface is clicked.
 *
 * @author lare96 
 */
public final class CloseInterfaceMessageReader extends GameMessageReader<CloseInterfaceEvent> {

    @Override
    public CloseInterfaceEvent decode(Player player, GameMessage msg) {
        return new CloseInterfaceEvent(player);
    }
}