package io.luna.net.codec.game;

import io.luna.net.codec.IsaacCipher;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.GameMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * A {@link MessageToByteEncoder} implementation that encodes game messages.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class GameMessageEncoder extends MessageToByteEncoder<GameMessage> {

    /**
     * The encryptor.
     */
    private final IsaacCipher encryptor;

    /**
     * Creates a new {@link GameMessageEncoder}.
     *
     * @param encryptor The encryptor.
     */
    public GameMessageEncoder(IsaacCipher encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public void encode(ChannelHandlerContext ctx, GameMessage msg, ByteBuf out) throws Exception {
        try {
            out.writeByte(msg.getOpcode() + encryptor.nextInt());
            if (msg.getType() == MessageType.VAR) {
                out.writeByte(msg.getSize());
            } else if (msg.getType() == MessageType.VAR_SHORT) {
                out.writeShort(msg.getSize());
            }
            out.writeBytes(msg.getPayload().getBuffer());
        } finally {
            msg.getPayload().releaseAll();
        }
    }
}
