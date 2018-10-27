package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data for when a close button
 * on an interface is clicked.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class CloseWindowMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        player.getInterfaces().close();
        return null;
    }
}