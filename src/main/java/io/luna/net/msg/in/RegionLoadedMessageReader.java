package io.luna.net.msg.in;

import io.luna.game.event.impl.RegionLoadedEvent;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when the client loads a region.
 *
 * @author lare96
 */
public final class RegionLoadedMessageReader extends GameMessageReader<RegionLoadedEvent> {

    @Override
    public RegionLoadedEvent decode(Player player, GameMessage msg) {
        if (player.isRegionChanged()) {
            World world = player.getWorld();
            player.setRegionChanged(false);
            world.getChunks().sendViewableEntities(player);
        }
        return new RegionLoadedEvent(player);
    }


}
