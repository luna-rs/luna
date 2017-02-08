package io.luna.net.msg;

import io.luna.game.event.Event;
import io.luna.game.model.mobile.Player;
import io.luna.util.ThreadUtils;

/**
 * A model containing data describing incoming game packets.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class MessageRepository {

    /**
     * A generic packet listener.
     */
    private static final MessageReader GENERIC_LISTENER = new MessageReader() {
        @Override
        public Event read(Player player, GameMessage msg) throws Exception {
            return null;
        }
    };

    /**
     * The incoming packet sizes.
     */
    private final int[] sizes = new int[257];

    /**
     * The incoming packet listeners.
     */
    private final MessageReader[] messageReaders = new MessageReader[257];

    /**
     * Creates a new {@link MessageRepository}.
     */
    public MessageRepository() {
        ThreadUtils.ensureInitThread();
    }

    /**
     * Adds new data describing an incoming packet.
     */
    public void addHandler(int opcode, int size, String messageReaderName) throws ReflectiveOperationException {
        ThreadUtils.ensureInitThread();

        sizes[opcode] = size;

        if (messageReaderName != null) {
            Class<?> messageReaderClass = Class.forName("io.luna.net.msg.in." + messageReaderName);
            messageReaders[opcode] = (MessageReader) messageReaderClass.newInstance();
        } else {
            messageReaders[opcode] = GENERIC_LISTENER;
        }
    }

    /**
     * Retrieves an incoming packet's size.
     */
    public int getSize(int opcode) {
        return sizes[opcode];
    }

    /**
     * Retrieves an incoming packet's listener.
     */
    public MessageReader getHandler(int opcode) {
        return messageReaders[opcode];
    }
}
