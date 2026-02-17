package io.luna.game.model.chunk;

/**
 * Represents a pending update request for a {@link ChunkUpdatable} within a {@link Chunk}.
 * <p>
 * The chunk update system enqueues update requests to ensure synchronization of updatables. Each request bundles:
 * <ul>
 *     <li>the target {@link ChunkUpdatable}</li>
 *     <li>the {@link ChunkUpdatableMessage} describing what should be sent (show/hide/update)</li>
 *     <li>a persistence flag indicating whether the request should remain queued/re-applied across cycles</li>
 * </ul>
 * <p>
 * <b>Persistence:</b> A persistent request typically represents state that should continue to be enforced for players
 * entering/leaving visibility (e.g., an object that should remain shown/hidden).
 *
 * @author lare96
 */
public final class ChunkUpdatableRequest {

    /**
     * The updatable entity this request targets.
     */
    private final ChunkUpdatable updatable;

    /**
     * The update message to apply/send for {@link #updatable}.
     */
    private final ChunkUpdatableMessage message;

    /**
     * Whether this request is persistent.
     */
    private final boolean persistent;

    /**
     * Creates a new {@link ChunkUpdatableRequest}.
     *
     * @param updatable The updatable this request targets.
     * @param message The message describing the update.
     * @param persistent Whether the request should persist beyond a single processing cycle.
     */
    public ChunkUpdatableRequest(ChunkUpdatable updatable, ChunkUpdatableMessage message, boolean persistent) {
        this.updatable = updatable;
        this.message = message;
        this.persistent = persistent;
    }

    /**
     * Returns the target {@link ChunkUpdatable}.
     *
     * @return The updatable.
     */
    public ChunkUpdatable getUpdatable() {
        return updatable;
    }

    /**
     * Returns the update message.
     *
     * @return The message.
     */
    public ChunkUpdatableMessage getMessage() {
        return message;
    }

    /**
     * Returns whether this request is persistent.
     *
     * @return {@code true} if persistent.
     */
    public boolean isPersistent() {
        return persistent;
    }
}
