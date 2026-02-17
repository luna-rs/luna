package io.luna.game.model.chunk;

import io.luna.game.model.mob.Player;

/**
 * Represents an entity (or entity-like component) that can be updated through the chunk update system.
 * <p>
 * Visibility of each request is controlled by the {@link ChunkUpdatableView} returned from
 * {@link #computeCurrentView()}. Implementations should return:
 * <ul>
 *     <li>{@link ChunkUpdatableView#globalView()} for world-visible updatables</li>
 *     <li>{@link ChunkUpdatableView#localView(Player)} (or a viewer set) for private/local effects</li>
 * </ul>
 *
 * @author lare96
 */
public interface ChunkUpdatable {

    /**
     * Computes the current visibility view for this updatable.
     * <p>
     * This method may be called frequently while collecting updates for players, so implementations should avoid
     * expensive work and prefer returning cached views where possible.
     *
     * @return The view that determines which players can see queued updates for this updatable.
     */
    ChunkUpdatableView computeCurrentView();
}
