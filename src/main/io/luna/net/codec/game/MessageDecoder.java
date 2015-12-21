package io.luna.net.codec.game;

import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.IsaacCipher;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.MessageRepository;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A {@link ByteToMessageDecoder} implementation that decodes all {@link ByteBuf}s into {@link GameMessage}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class MessageDecoder extends ByteToMessageDecoder {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(MessageDecoder.class);

    /**
     * The ISAAC that will decrypt incoming messages.
     */
    private final IsaacCipher decryptor;

    /**
     * The repository containing data for incoming messages.
     */
    private final MessageRepository messageRepository;

    /**
     * The state of the message currently being decoded.
     */
    private State state = State.OPCODE;

    /**
     * The opcode of the message currently being decoded.
     */
    private int opcode = -1;

    /**
     * The size of the message currently being decoded.
     */
    private int size = -1;

    /**
     * The message that was decoded and needs to be queued.
     */
    private Optional<GameMessage> currentMessage = Optional.empty();

    /**
     * Creates a new {@link MessageDecoder}.
     *
     * @param decryptor The decryptor for this decoder.
     * @param messageRepository The repository containing data for incoming messages.
     */
    public MessageDecoder(IsaacCipher decryptor, MessageRepository messageRepository) {
        this.decryptor = decryptor;
        this.messageRepository = messageRepository;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (state) {
        case OPCODE:
            opcode(in);
            break;
        case SIZE:
            size(in);
            break;
        case PAYLOAD:
            payload(in);
            break;
        }
        currentMessage.ifPresent(out::add);
        currentMessage.ifPresent($it -> currentMessage = Optional.empty());
    }

    /**
     * Decodes the opcode of the {@link GameMessage}.
     *
     * @param in The data being decoded.
     */
    private void opcode(ByteBuf in) {
        if (in.isReadable()) {
            opcode = in.readUnsignedByte();
            opcode = (opcode - decryptor.getKey()) & 0xFF;
            size = messageRepository.getSize(opcode);

            if (size == 0) {
                queueMessage(opcode, size, PooledByteBufAllocator.DEFAULT.buffer(0, 0));
                return;
            }

            state = size == -1 || size == -2 ? State.SIZE : State.PAYLOAD;
        }
    }

    /**
     * Decodes the size of the {@link GameMessage}.
     *
     * @param in The data being decoded.
     */
    private void size(ByteBuf in) {
        int bytes = size == -1 ? Byte.BYTES : Short.BYTES;

        if (in.isReadable(bytes)) {
            size = 0;

            for (int i = 0; i < bytes; i++) {
                size |= in.readUnsignedByte() << 8 * (bytes - 1 - i);
            }

            state = State.PAYLOAD;
        }
    }

    /**
     * Decodes the payload of the {@link GameMessage}.
     *
     * @param in The data being decoded.
     */
    private void payload(ByteBuf in) {
        if (in.isReadable(size)) {
            queueMessage(opcode, size, in.readBytes(size));
        }
    }

    /**
     * Prepares a {@link GameMessage} to be queued upstream and handled on the main game thread.
     *
     * @param opcode The opcode of the {@code GameMessage}.
     * @param size The size of the {@code GameMessage}.
     * @param payload The payload of the {@code GameMessage}.
     */
    private void queueMessage(int opcode, int size, ByteBuf payload) {
        checkArgument(opcode >= 0, "opcode < 0");
        checkArgument(size >= 0, "size < 0");

        try {
            if (messageRepository.getHandler(opcode) == null) {
                LOGGER.debug("No InboundGameMessage assigned to [opcode= " + opcode + "]");
                currentMessage = Optional.empty();
                return;
            }

            currentMessage = Optional.of(new GameMessage(opcode, size, ByteMessage.create(payload)));
        } finally {
            resetState();
        }
    }

    /**
     * Resets the state of this {@code MessageDecoder} to its default.
     */
    private void resetState() {
        opcode = -1;
        size = -1;
        state = State.OPCODE;
    }

    /**
     * An enumerated type whose elements represent all of the possible states of this {@code MessageDecoder}.
     */
    private enum State {
        OPCODE,
        SIZE,
        PAYLOAD
    }
}
