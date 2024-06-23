package io.luna.net.msg;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;

import static com.google.common.base.Preconditions.checkState;

/**
 * An {@link Iterable} implementation containing mappings of {@link GameMessageReader} types to their opcodes.
 *
 * @author lare96
 */
public final class GameMessageRepository implements Iterable<GameMessageReader<?>> {

    /**
     * If this repository is locked.
     */
    private volatile boolean locked;

    /**
     * An array of message decoders. This array is effectively immutable since it is never exposed outside of this
     * class.
     */
    private final GameMessageReader<?>[] readers = new GameMessageReader[256];

    /**
     * Adds a reader to this repository.
     *
     * @param messageReader The reader to add.
     * @throws IllegalStateException If this repository is locked.
     */
    public void put(GameMessageReader<?> messageReader) throws IllegalStateException {
        checkState(!locked, "This repository is locked.");

        int opcode = messageReader.getOpcode();
        checkState(get(opcode) == null, "Reader already exists for opcode [" + opcode + "].");
        readers[opcode] = messageReader;
    }

    /**
     * Retrieves a reader from this repository.
     *
     * @param opcode The opcode of the reader to retrieve.
     * @return The reader.
     */
    public GameMessageReader<?> get(int opcode) {
        return readers[opcode];
    }

    /**
     * Retrieves the size from a message reader.
     *
     * @param opcode The opcode of the size to retrieve.
     * @return The size of a message.
     */
    public int getSize(int opcode) {
        return get(opcode).getSize();
    }

    /**
     * Locks this repository, making it read-only. Has no effect if already locked.
     */
    public void lock() {
        locked = true;
    }

    @Override
    public UnmodifiableIterator<GameMessageReader<?>> iterator() {
        return Iterators.forArray(readers);
    }
}
