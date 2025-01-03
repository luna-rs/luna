package io.luna.net.codec.game;

import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.IsaacCipher;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.GameMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link GameMessageEncoder}.
 *
 * @author lare96 
 */
final class GameMessageEncoderTest {

    static IsaacCipher isaac;
    static GameMessageEncoder encoder;
    static ChannelHandlerContext ctx;
    static byte[] payload;
    static ByteBuf buffer;

    @BeforeAll
    static void initData() {
        isaac = new IsaacCipher(new int[]{0, 0, 0, 0});
        encoder = new GameMessageEncoder(isaac);
        ctx = mock(ChannelHandlerContext.class);
        payload = "test".getBytes();
        buffer = Unpooled.buffer();
    }

    @Test
    void encodeMessages() throws Exception {
        // Has to be done in one test since there's no support for ordering yet.

        // Fixed length test.
        var msg = ByteMessage.message(54, MessageType.FIXED);
        msg.putBytes(payload);
        encoder.encode(ctx, new GameMessage(msg.getOpcode(), msg.getType(), msg), buffer);

        assertEquals(41, buffer.readUnsignedByte());
        assertEquals('t', buffer.readByte());
        assertEquals('e', buffer.readByte());
        assertEquals('s', buffer.readByte());
        assertEquals('t', buffer.readByte());
        buffer.clear();


        // Variable length test.
        msg = ByteMessage.message(54, MessageType.VAR);
        msg.putBytes(payload);
        encoder.encode(ctx, new GameMessage(msg.getOpcode(), msg.getType(), msg), buffer);

        assertEquals(195, buffer.readUnsignedByte());
        assertEquals(4, buffer.readByte());
        assertEquals('t', buffer.readByte());
        assertEquals('e', buffer.readByte());
        assertEquals('s', buffer.readByte());
        assertEquals('t', buffer.readByte());
        buffer.clear();

        // Variable short length test.
        msg = ByteMessage.message(54, MessageType.VAR_SHORT);
        msg.putBytes(payload);
        encoder.encode(ctx, new GameMessage(msg.getOpcode(), msg.getType(), msg), buffer);

        assertEquals(88, buffer.readUnsignedByte());
        assertEquals(4, buffer.readUnsignedShort());
        assertEquals('t', buffer.readByte());
        assertEquals('e', buffer.readByte());
        assertEquals('s', buffer.readByte());
        assertEquals('t', buffer.readByte());
    }
}
