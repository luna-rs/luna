package io.luna.game.model.chunk;

/**
 * Represents a pending update request for a {@link ChunkUpdatable} within a {@link Chunk}.
 *
 * @author lare96
 */
public final class ChunkUpdatableRequest {

    /**
     * The {@link ChunkUpdatable} this update is for.
     */
    private final ChunkUpdatable updatable;

    /**
     * The update message.
     */
    private final ChunkUpdatableMessage message;

    /**
     * If this request is persistent.
     */
    private final boolean persistent;

    /**
     * Creates a new {@link ChunkUpdatableRequest}.
     *
     * @param updatable The {@link ChunkUpdatable} this update is for.
     * @param message The update message.
     * @param persistent If this request is persistent.
     */
    public ChunkUpdatableRequest(ChunkUpdatable updatable, ChunkUpdatableMessage message, boolean persistent) {
        this.updatable = updatable;
        this.message = message;
        this.persistent = persistent;
    }

    /**
     * @return The {@link ChunkUpdatable} this update is for.
     */
    public ChunkUpdatable getUpdatable() {
        return updatable;
    }

    /**
     * @return The update message.
     */
    public ChunkUpdatableMessage getMessage() {
        return message;
    }

    /**
     * @return If this request is persistent.
     */
    public boolean isPersistent() {
        return persistent;
    }
}
