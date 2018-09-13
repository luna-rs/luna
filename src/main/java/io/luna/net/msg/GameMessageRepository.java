package io.luna.net.msg;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkState;

/**
 * An {@link Iterable} implementation containing mappings of {@link GameMessageListener}s to their opcode
 * identifiers, and functions to retrieve said message listeners.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class GameMessageRepository implements Iterable<GameMessageRepository.GameMessageListener> {

    /**
     * A listener containing data describing and functions to handle decoded game messages.
     *
     * @author lare96 <http://github.com/lare96>
     */
    public static final class GameMessageListener {

        /**
         * The opcode.
         */
        private final int opcode;

        /**
         * The expected size.
         */
        private final int size;

        /**
         * The message reader.
         */
        private final GameMessageReader reader;

        /**
         * Creates a new {@link GameMessageListener}.
         *
         * @param opcode The opcode.
         * @param size The expected size.
         * @param reader The message reader.
         */
        public GameMessageListener(int opcode, int size, GameMessageReader reader) {
            this.opcode = opcode;
            this.size = size;
            this.reader = reader;
        }

        @Override
        public final boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof GameMessageListener) {
                GameMessageListener other = (GameMessageListener) obj;
                return opcode == other.opcode;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(opcode);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("opcode", opcode).add("size", size).toString();
        }

        /**
         * @return The opcode.
         */
        public int getOpcode() {
            return opcode;
        }

        /**
         * @return The size.
         */
        public int getSize() {
            return size;
        }

        /**
         * @return The message reader.
         */
        public GameMessageReader getReader() {
            return reader;
        }
    }

    /**
     * If this repository is locked.
     */
    private volatile boolean locked;

    /**
     * An array of message listeners. This array is effectively immutable since it is never exposed
     * outside of this class.
     */
    private final GameMessageListener[] listeners = new GameMessageListener[257];

    /**
     * Adds a listener to this repository.
     *
     * @param listener The listener to add.
     * @throws IllegalStateException If this repository is locked.
     */
    public void put(GameMessageListener listener) throws IllegalStateException {
        checkState(!locked, "This repository is locked.");

        int opcode = listener.getOpcode();
        checkState(get(opcode) == null, "Listener already exists for opcode [" + opcode + "].");
        listeners[opcode] = listener;
    }

    /**
     * Retrieves a listener from this repository.
     *
     * @param opcode The opcode of the listener to retrieve.
     * @return The listener.
     */
    public GameMessageListener get(int opcode) {
        return listeners[opcode];
    }

    /**
     * Retrieves the size of a message.
     *
     * @param opcode The opcode of the size to retrieve.
     * @return The size of the message.
     */
    public int getSize(int opcode) {
        return get(opcode).getSize();
    }

    /**
     * Retrieves the game message reader.
     *
     * @param opcode The opcode of the reader to retrieve.
     * @return The game message reader.
     */
    public GameMessageReader getReader(int opcode) {
        return get(opcode).getReader();
    }

    /**
     * Locks this repository, making it read-only. Has no effect if already locked.
     */
    public void lock() {
        locked = true;
    }

    @NotNull
    @Override
    public UnmodifiableIterator<GameMessageListener> iterator() {
        return Iterators.forArray(listeners);
    }
}
