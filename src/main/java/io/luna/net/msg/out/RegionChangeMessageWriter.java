package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that sends a message containing the current region.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class RegionChangeMessageWriter extends GameMessageWriter {

    @Override
    public ByteMessage write(Player player) {
        var position = player.getPosition();
        var msg = ByteMessage.message(73);
        msg.putShort(position.getBottomLeftChunkX() + 6, ValueType.ADD);
        msg.putShort(position.getBottomLeftChunkY() + 6);
        return msg;
    }
}
