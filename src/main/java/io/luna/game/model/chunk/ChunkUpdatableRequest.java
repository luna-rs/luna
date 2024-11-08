package io.luna.game.model.chunk;

import io.luna.game.model.StationaryEntity;

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
     * Creates a new {@link ChunkUpdatableRequest}.
     *
     * @param entity The {@link StationaryEntity} this update is for.
     * @param owner The player to update for.
     * @param message The update message.
     */
    public ChunkUpdatableRequest(ChunkUpdatable updatable, ChunkUpdatableMessage message) {
        this.updatable = updatable;
        this.message = message;
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
}
