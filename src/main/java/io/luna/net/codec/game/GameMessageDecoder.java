package io.luna.net.codec.game;

import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.IsaacCipher;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.MessageRepository;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A {@link ByteToMessageDecoder} implementation that decodes game messages.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class GameMessageDecoder extends ByteToMessageDecoder {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The decryptor.
     */
    private final IsaacCipher decryptor;

    /**
     * The message repository.
     */
    private final MessageRepository messageRepository;

    /**
     * The current state.
     */
    private State state = State.OPCODE;

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
     * The decoded message.
     */
    private Optional<GameMessage> currentMessage = Optional.empty();

    /**
     * Creates a new {@link GameMessageDecoder}.
     *
     * @param decryptor The decryptor.
     * @param messageRepository The message repository.
     */
    public GameMessageDecoder(IsaacCipher decryptor, MessageRepository messageRepository) {
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
        currentMessage.ifPresent(msg -> {
            out.add(msg);
            currentMessage = Optional.empty();
        });
    }

    /**
     * Decodes the opcode.
     */
    private void opcode(ByteBuf in) {
        if (in.isReadable()) {
            opcode = in.readUnsignedByte();
            opcode = (opcode - decryptor.nextInt()) & 0xFF;
            size = messageRepository.getSize(opcode);

            if (size == -1) {
                type = MessageType.VAR;
            } else if (size == -2) {
                type = MessageType.VAR_SHORT;
            } else {
                type = MessageType.FIXED;
            }

            if (size == 0) {
                queueMsg(Unpooled.EMPTY_BUFFER);
                return;
            }
            state = size == -1 || size == -2 ? State.SIZE : State.PAYLOAD;
        }
    }

    /**
     * Decodes the size.
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
     * Decodes the payload.
     */
    private void payload(ByteBuf in) {
        if (in.isReadable(size)) {
            ByteBuf newBuffer = in.readBytes(size);
            queueMsg(newBuffer);
        }
    }

    /**
     * Prepares a packet to be queued upstream.
     */
    private void queueMsg(ByteBuf payload) {
        checkState(opcode >= 0, "opcode < 0");
        checkState(size >= 0, "size < 0");
        checkState(type != MessageType.RAW, "type == MessageType.RAW");
        checkState(!currentMessage.isPresent(), "message already in queue");

        try {
            if (messageRepository.getHandler(opcode) == null) {
                LOGGER.debug("No InboundGameMessage assigned to [opcode={}]", box(opcode));
                currentMessage = Optional.empty();
                payload.release();
                return;
            }

            currentMessage = Optional.of(new GameMessage(opcode, type, ByteMessage.wrap(payload)));
        } finally {
            resetState();
        }
    }

    /**
     * Resets the decoder's state.
     */
    private void resetState() {
        opcode = -1;
        size = -1;
        state = State.OPCODE;
    }

    /**
     * An enum representing decoder states.
     */
    private enum State {
        OPCODE,
        SIZE,
        PAYLOAD
    }
}
