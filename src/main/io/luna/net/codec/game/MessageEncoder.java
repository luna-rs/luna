package io.luna.net.codec.game;

import io.luna.net.codec.IsaacCipher;
import io.luna.net.msg.GameMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * A {@link MessageToByteEncoder} implementation that encodes all {@link GameMessage}s into {@link ByteBuf}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class MessageEncoder extends MessageToByteEncoder<GameMessage> {

    /**
     * The encryptor for this message.
     */
    private final IsaacCipher encryptor;

    /**
     * Creates a new {@link MessageEncoder}.
     *
     * @param encryptor The encryptor for this encoder.
     */
    public MessageEncoder(IsaacCipher encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, GameMessage msg, ByteBuf out) throws Exception {
        ByteBuf toEncode = msg.getPayload().getBuffer();

        toEncode.setByte(0, toEncode.getByte(0) + encryptor.getKey());

        out.writeBytes(toEncode);
        toEncode.release();
    }
}
