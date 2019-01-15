package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.PlayerClickEvent.PlayerFourthClickEvent;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent on Player interaction menu clicks.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerClickMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int opcode = msg.getOpcode();
        switch (opcode) {
            case 139:
                return fourthIndex(player, msg.getPayload());
        }
        return null;
    }

    /**
     * The fourth interaction index.
     *
     * @param player The player.
     * @param msg The buffer.
     * @return The event to post.
     */
    private Event fourthIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(true, ByteOrder.LITTLE);
        World world = player.getWorld();
        Player other = world.getPlayers().get(index);
        // TODO Change isViewable to canFindPath, after pathfinding
        if (other == null || !other.isViewableFrom(player) || other.equals(player)) {
            return null;
        }
        return new PlayerFourthClickEvent(player, index, other);
    }
}