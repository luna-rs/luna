package io.luna.net.msg;

import io.luna.game.event.Event;
import io.luna.game.model.mob.Player;
import io.luna.util.ThreadUtils;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkState;

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

    private final AtomicBoolean initialized = new AtomicBoolean();
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
        checkState(!initialized.get(), "Cannot add handlers to an initialized message repository.");

        sizes[opcode] = size;

        if (messageReaderName != null) {
            Class<?> messageReaderClass = Class.forName("io.luna.net.msg.in." + messageReaderName);
            messageReaders[opcode] = (MessageReader) messageReaderClass.newInstance();
        } else {
            messageReaders[opcode] = GENERIC_LISTENER;
        }
    }

    public void setInitialized() {
        checkState(initialized.compareAndSet(false, true), "Message repository already initialized.");
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
