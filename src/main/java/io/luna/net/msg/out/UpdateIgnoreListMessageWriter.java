package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} implementation that sends the client a copy of the player's ignore list.
 *
 * @author lare96
 */
public final class UpdateIgnoreListMessageWriter extends GameMessageWriter {

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(226, MessageType.VAR_SHORT, buffer);
        for (long name : player.getIgnores()) {
            msg.putLong(name);
        }
        return msg;
    }
}
