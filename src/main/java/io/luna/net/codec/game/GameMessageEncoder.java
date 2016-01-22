package io.luna.net.codec.game;

import io.luna.net.codec.IsaacCipher;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.GameMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link MessageToByteEncoder} implementation that encodes all {@link GameMessage}s into {@link ByteBuf}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class GameMessageEncoder extends MessageToByteEncoder<GameMessage> {

    /**
     * The encryptor for this message.
     */
    private final IsaacCipher encryptor;

    /**
     * Creates a new {@link GameMessageEncoder}.
     *
     * @param encryptor The encryptor for this encoder.
     */
    public GameMessageEncoder(IsaacCipher encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, GameMessage msg, ByteBuf out) throws Exception {
        checkState(msg.getOpcode() >= 0, "opcode < 0");
        checkState(msg.getSize() >= 0, "size < 0");
        checkState(msg.getType() != MessageType.RAW, "type == MessageType.RAW");

        out.writeByte(msg.getOpcode() + encryptor.nextKey());
        if (msg.getType() == MessageType.VARIABLE) {
            out.writeByte(msg.getSize());
        } else if (msg.getType() == MessageType.VARIABLE_SHORT) {
            out.writeShort(msg.getSize());
        }
        out.writeBytes(msg.getPayload().getBuffer());

        msg.getPayload().release();
    }
}
