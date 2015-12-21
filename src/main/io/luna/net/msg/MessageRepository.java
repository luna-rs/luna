package io.luna.net.msg;

import static com.google.common.base.Preconditions.checkState;

/**
 * A repository that contains data related to incoming {@link GameMessage}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class MessageRepository {

    /**
     * An array of integers that contain the incoming message sizes.
     */
    private final int[] sizes = new int[257];

    /**
     * An array of {@link InboundGameMessage}s that act as listeners for incoming messages.
     */
    private final InboundGameMessage[] inboundHandlers = new InboundGameMessage[257];

    /**
     * Creates a new {@link MessageRepository}.
     */
    public MessageRepository() {
        String threadName = Thread.currentThread().getName();
        checkState(threadName.equals("LunaMainThread"), "can only be done during initialization");
    }

    /**
     * Adds a new {@link InboundGameMessage} handler along with its size.
     *
     * @param opcode The opcode of the message handler.
     * @param size The size of the message.
     * @param inboundMessageName The class name of the {@link InboundGameMessage}, implicitly prefixed with the {@code
     * io.luna.net.msg.in} package.
     * @throws ReflectiveOperationException If any errors occur while instantiating the {@link InboundGameMessage}.
     */
    public void addInboundMessage(int opcode, int size, String inboundMessageName) throws ReflectiveOperationException {
        String threadName = Thread.currentThread().getName();
        checkState(threadName.equals("LunaInitializationThread"), "can only be done during initialization");

        Class<?> inboundMessageClass = Class.forName("io.luna.net.msg.in." + inboundMessageName);

        sizes[opcode] = size;
        inboundHandlers[opcode] = (InboundGameMessage) inboundMessageClass.newInstance();
    }

    /**
     * Retrieves the size of a message by {@code opcode}.
     *
     * @param opcode The opcode to retrieve the size of.
     * @return The size of {@code opcode}.
     */
    public int getSize(int opcode) {
        return sizes[opcode];
    }

    /**
     * Retrieves the incoming message handler for {@code opcode}.
     *
     * @param opcode The opcode to retrieve the message handler for.
     * @return The message handler for {@code opcode}, never {@code null}.
     */
    public InboundGameMessage getHandler(int opcode) {
        return inboundHandlers[opcode];
    }
}
