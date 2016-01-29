package io.luna.codec.game;

import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.IsaacCipher;
import io.luna.net.codec.MessageType;
import io.luna.net.codec.game.GameMessageEncoder;
import io.luna.net.msg.GameMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A test that ensures the {@link GameMessageEncoder} is functioning correctly.
 *
 * @author lare96 <http://github.org/lare96>
 * @author Graham
 */
public final class GameMessageEncoderTest {

    @Test
    public void testEncode() throws Exception {

        // generates 243, 141, 34, -223, 121...
        IsaacCipher isaac = new IsaacCipher(new int[] { 0, 0, 0, 0 });
        GameMessageEncoder encoder = new GameMessageEncoder(isaac);

        byte[] payload = "test".getBytes();
        ByteBuf buffer = Unpooled.buffer();

        // fixed length messages
        ByteMessage msg = ByteMessage.message(54, MessageType.FIXED);
        msg.putBytes(payload);
        encoder.encode(null, new GameMessage(msg.getOpcode(), msg.getType(), msg), buffer);

        assertEquals(297, buffer.readUnsignedByte());
        assertEquals('t', buffer.readChar());
        assertEquals('e', buffer.readChar());
        assertEquals('s', buffer.readChar());
        assertEquals('t', buffer.readChar());

        buffer.clear();

        // variable length messages
        msg = ByteMessage.message(54, MessageType.VARIABLE);
        msg.putBytes(payload);
        encoder.encode(null, new GameMessage(msg.getOpcode(), msg.getType(), msg), buffer);

        assertEquals(195, buffer.readUnsignedByte());
        assertEquals(4, buffer.readByte());
        assertEquals('t', buffer.readChar());
        assertEquals('s', buffer.readChar());
        assertEquals('e', buffer.readChar());
        assertEquals('t', buffer.readChar());

        buffer.clear();

        // variable short length messages
        msg = ByteMessage.message(54, MessageType.VARIABLE_SHORT);
        msg.putBytes(payload);
        encoder.encode(null, new GameMessage(msg.getOpcode(), msg.getType(), msg), buffer);

        assertEquals(88, buffer.readUnsignedByte());
        assertEquals(4, buffer.readUnsignedShort());
        assertEquals('t', buffer.readChar());
        assertEquals('e', buffer.readChar());
        assertEquals('s', buffer.readChar());
        assertEquals('t', buffer.readChar());
    }

    @Test(expected = IllegalStateException.class)
    public void testInvalidOpcode() throws Exception {
        IsaacCipher isaac = new IsaacCipher(new int[] { 0, 0, 0, 0 });
        GameMessageEncoder encoder = new GameMessageEncoder(isaac);

        ByteMessage msg = ByteMessage.message(-638, MessageType.FIXED);
        encoder.encode(null, new GameMessage(msg.getOpcode(), msg.getType(), msg), Unpooled.buffer());
    }

    @Test(expected = IllegalStateException.class)
    public void testInvalidType() throws Exception {
        IsaacCipher isaac = new IsaacCipher(new int[] { 0, 0, 0, 0 });
        GameMessageEncoder encoder = new GameMessageEncoder(isaac);

        ByteMessage msg = ByteMessage.message(44, MessageType.RAW);
        encoder.encode(null, new GameMessage(msg.getOpcode(), msg.getType(), msg), Unpooled.buffer());
    }
}
