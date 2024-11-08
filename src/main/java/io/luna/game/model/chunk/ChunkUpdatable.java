package io.luna.game.model.chunk;

/**
 * A type which extends this can be updated through a {@link ChunkUpdatableRequest} based on a
 * defined {@link ChunkUpdatableView}.
 *
 * @author lare96
 */
public interface ChunkUpdatable {

    /**
     * Returns the {@link ChunkUpdatableView} that will be used to display this type.
     */
    ChunkUpdatableView computeCurrentView();
}
