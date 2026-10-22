package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * Clears the client's current hint arrow using hint type zero.
 *
 * @author lare96
 */
public final class ClearHintMessageWriter extends GameMessageWriter {
    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage message = ByteMessage.message(199, buffer);
        message.put(0);
        message.putShort(0);
        message.put(0);
        message.putShort(0);
        return message;
    }
}
