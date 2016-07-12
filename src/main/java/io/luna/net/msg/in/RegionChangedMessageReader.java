package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.RegionChangedEvent;
import io.luna.game.model.mobile.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.MessageReader;

/**
 * A {@link MessageReader} implementation that decodes data sent when the region changes.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class RegionChangedMessageReader extends MessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        if (player.isRegionChanged()) {
            player.setRegionChanged(false);
            return RegionChangedEvent.INSTANCE;
        }
        return null;
    }
}
