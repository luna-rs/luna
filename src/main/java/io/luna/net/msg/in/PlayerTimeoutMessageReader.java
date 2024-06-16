package io.luna.net.msg.in;

import io.luna.game.event.impl.NullEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when the player doesn't perform any actions
 * for a while.
 *
 * @author lare96
 */
public final class PlayerTimeoutMessageReader extends GameMessageReader<NullEvent> {

    @Override
    public NullEvent decode(Player player, GameMessage msg) {
        if (!player.getTimeout().isRunning()) {
            player.getTimeout().start();
        } else if(player.getTimeout().elapsed().toMinutes() >= 5) {
            player.logout();
        }
        return NullEvent.INSTANCE;
    }
}
