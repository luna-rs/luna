package io.luna.net.msg.in;

import io.luna.game.event.impl.PlayerTimeoutEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when the player doesn't perform any actions
 * for a while.
 *
 * @author lare96
 */
public final class PlayerTimeoutMessageReader extends GameMessageReader<PlayerTimeoutEvent> {

    @Override
    public PlayerTimeoutEvent decode(Player player, GameMessage msg) {
        return new PlayerTimeoutEvent(player);
    }
}
