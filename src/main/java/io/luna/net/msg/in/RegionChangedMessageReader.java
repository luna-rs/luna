package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.RegionChangedEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when the region changes.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class RegionChangedMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        if (player.isRegionChanged()) {
            player.setRegionChanged(false);
            return new RegionChangedEvent(player);
        }
        return null;
    }
}
