package io.luna.net.msg;

import io.luna.util.ThreadUtils;

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
     * An array of {@link MessageReader}s that act as listeners for incoming messages.
     */
    private final MessageReader[] messageReaders = new MessageReader[257];

    /**
     * Creates a new {@link MessageRepository}.
     */
    public MessageRepository() {
        ThreadUtils.ensureInitThread();
    }

    /**
     * Adds a new {@link MessageReader} handler along with its size.
     *
     * @param opcode The opcode of the message handler.
     * @param size The size of the message.
     * @param messageReaderName The class name of the {@link MessageReader}, implicitly prefixed with the {@code
     * io.luna.net.msg.in} package.
     * @throws ReflectiveOperationException If any errors occur while instantiating the {@link MessageReader}.
     */
    public void addHandler(int opcode, int size, String messageReaderName) throws ReflectiveOperationException {
        ThreadUtils.ensureInitThread();

        Class<?> messageReaderClass = Class.forName("io.luna.net.msg.in." + messageReaderName);
        sizes[opcode] = size;
        messageReaders[opcode] = (MessageReader) messageReaderClass.newInstance();
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
    public MessageReader getHandler(int opcode) {
        return messageReaders[opcode];
    }
}
