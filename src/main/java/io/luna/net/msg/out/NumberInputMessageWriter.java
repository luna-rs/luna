package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.overlay.NumberInput;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} implementation that opens an "Enter amount" input dialogue. Use
 * {@link NumberInput} instead of using this packet directly.
 *
 * @author lare96
 */
public final class NumberInputMessageWriter extends GameMessageWriter {

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        return ByteMessage.message(58, buffer);
    }
}