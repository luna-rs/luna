package io.luna.net.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A {@link ByteToMessageDecoder} implementation that will progressively decode messages sent from
 * the client.
 *
 * @param <E> The Enum type representing the state of this decoder.
 * @author lare96 
 */
public abstract class ProgressiveMessageDecoder<E extends Enum<E>> extends ByteToMessageDecoder {

    /**
     * The initial state.
     */
    private final E initialState;

    /**
     * The current state.
     */
    private E state;

    /**
     * Creates a new {@link ProgressiveMessageDecoder}.
     *
     * @param initialState The initial state.
     */
    public ProgressiveMessageDecoder(E initialState) {
        this.initialState = requireNonNull(initialState);
        state = initialState;
    }

    @Override
    protected final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        Object decodedMessage = decodeMsg(ctx, in, state);
        if (decodedMessage != null) {

            // Message will be received by the LunaUpstreamHandler.
            out.add(decodedMessage);
            reset();
        }
    }

    /**
     * Decodes a message. Will be called until there are no more bytes left to read.
     *
     * @param ctx The channel handler context.
     * @param in The buffer to read from.
     * @param state The current state.
     * @return The decoded message. {@code null} if no message was decoded.
     * @throws Exception If any errors occur while decoding messages.
     */
    protected abstract Object decodeMsg(ChannelHandlerContext ctx, ByteBuf in, E state) throws Exception;

    /**
     * Sets a new checkpoint state.
     *
     * @param newState The new state.
     */
    public final void checkpoint(E newState) {
        state = requireNonNull(newState);
    }

    /**
     * Resets the current state and forwards a call to {@link #resetState()}.
     */
    public final void reset() {
        state = initialState;
        resetState();
    }

    /**
     * A function called when this decoder is reset. Subclasses can override this to perform additional
     * reset operations.
     */
    protected void resetState() {

    }
}