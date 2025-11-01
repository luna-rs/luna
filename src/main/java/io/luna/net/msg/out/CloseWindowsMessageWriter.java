package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} implementation that will close all open interfaces.
 *
 * @author lare96
 */
public final class CloseWindowsMessageWriter extends GameMessageWriter {

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        return ByteMessage.message(29, buffer);
    }
}
