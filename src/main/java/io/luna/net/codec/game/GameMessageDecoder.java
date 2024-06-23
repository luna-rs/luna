package io.luna.net.codec.game;

import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.IsaacCipher;
import io.luna.net.codec.MessageType;
import io.luna.net.codec.ProgressiveMessageDecoder;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageRepository;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link ProgressiveMessageDecoder} implementation that decodes game messages.
 *
 * @author lare96
 */
public final class GameMessageDecoder extends ProgressiveMessageDecoder<GameMessageDecoder.DecodeState> {

    /**
     * An enumerated type representing game message decoding states.
     */
    enum DecodeState {
        OPCODE,
        SIZE,
        PAYLOAD
    }

    /**
     * The decryptor.
     */
    private final IsaacCipher decryptor;

    /**
     * The message repository.
     */
    private final GameMessageRepository repository;

    /**
     * The current opcode.
     */
    private int opcode = -1;

    /**
     * The current size.
     */
    private int size = -1;

    /**
     * The current message type.
     */
    private MessageType type = MessageType.RAW;

    /**
     * Creates a new {@link GameMessageDecoder}.
     *
     * @param decryptor The decryptor.
     * @param repository The message repository.
     */
    public GameMessageDecoder(IsaacCipher decryptor, GameMessageRepository repository) {
        super(DecodeState.OPCODE);
        this.decryptor = decryptor;
        this.repository = repository;
    }

    @Override
    protected Object decodeMsg(ChannelHandlerContext ctx, ByteBuf in, DecodeState state) {
        switch (state) {
            case OPCODE:
                return opcode(in);
            case SIZE:
                size(in);
                break;
            case PAYLOAD:
                return payload(in);
        }
        return null;
    }

    @Override
    protected void resetState() {
        opcode = -1;
        size = -1;
        type = MessageType.RAW;
    }

    /**
     * Decodes the opcode.
     *
     * @param in The buffer to read from.
     * @return The decoded game message.
     */
    private Object opcode(ByteBuf in) {
        if (in.isReadable()) {

            // Decode the message opcode.
            opcode = in.readUnsignedByte();
            opcode = (opcode - decryptor.nextInt()) & 0xFF;

            // Handle the message size.
            size = repository.getSize(opcode);
            switch (size) {

                // No size, don't have to decode size or payload.
                case 0:
                    type = MessageType.FIXED;
                    return createDecodedMessage(Unpooled.EMPTY_BUFFER);

                // Variable sized packet.
                case -1:
                    type = MessageType.VAR;
                    break;

                // Variable short sized packet.
                case -2:
                    type = MessageType.VAR_SHORT;
                    break;

                // Fixed size packet, only need to decode payload.
                default:
                    type = MessageType.FIXED;
                    break;
            }

            // Set the new checkpoint state.
            checkpoint(type == MessageType.FIXED ?
                    DecodeState.PAYLOAD : DecodeState.SIZE);
        }
        return null;
    }

    /**
     * Decodes the size.
     *
     * @param in The buffer to read from.
     */
    private void size(ByteBuf in) {
        int bytes = size == -1 ? Byte.BYTES : Short.BYTES;
        if (in.isReadable(bytes)) {

            // Decode size based on amount of bytes to read.
            size = 0;
            for (int i = 0; i < bytes; i++) {
                size |= in.readUnsignedByte() << 8 * (bytes - 1 - i);
            }

            // Set the new checkpoint state.
            checkpoint(DecodeState.PAYLOAD);
        }
    }

    /**
     * Decodes the payload.
     *
     * @param in The buffer to read from.
     * @return The decoded game message.
     */
    private Object payload(ByteBuf in) {
        if (in.isReadable(size)) {

            // Create payload using decoded size.
            ByteBuf newBuffer = in.readBytes(size);
            return createDecodedMessage(newBuffer);
        }
        return null;
    }

    /**
     * Creates a new game message Object from the decoded opcode, size, and payload.
     *
     * @param payload The data to wrap in a {@link ByteMessage}.
     * @return The decoded game message.
     */
    private GameMessage createDecodedMessage(ByteBuf payload) {
        checkState(type != MessageType.RAW, "Opcode was never decoded properly.");
        return new GameMessage(opcode, type, ByteMessage.wrap(payload));
    }
}
