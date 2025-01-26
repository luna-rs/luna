package io.luna.game.model;

import com.google.common.collect.ImmutableList;
import io.luna.LunaContext;
import io.luna.game.model.chunk.ChunkRepository;
import io.luna.game.model.chunk.ChunkUpdatable;
import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.chunk.ChunkUpdatableRequest;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessageWriter;

import java.util.Collections;
import java.util.Set;

/**
 * Represents a non-moving {@link Entity} type that is also a {@link ChunkUpdatable}.
 *
 * @author lare96
 */
public abstract class StationaryEntity extends Entity implements ChunkUpdatable {

    /**
     * Who this entity can be viewed by.
     */
    private final ChunkUpdatableView view;

    /**
     * The surrounding players. Initialized lazily, use {@link #getSurroundingPlayers()}.
     */
    private ImmutableList<Set<Player>> surroundingPlayers;

    /**
     * If this entity is hidden.
     */
    private boolean hidden = true;

    /**
     * Creates a new local {@link StationaryEntity}.
     *
     * @param context The context instance.
     * @param position The position.
     * @param type The entity type.
     * @param view Who this entity can be viewed by.
     */
    public StationaryEntity(LunaContext context, Position position, EntityType type, ChunkUpdatableView view) {
        super(context, position, type);
        this.view = view;
    }

    @Override
    public ChunkUpdatableView computeCurrentView() {
        return view;
    }

    /**
     * Stationary entities rely solely on identity when compared because they lack an index.
     */
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    /**
     * Stationary entities rely solely on identity when compared because they lack an index.
     */
    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    /**
     * Creates a {@link GameMessageWriter} that shows this entity.
     *
     * @param offset The chunk offset.
     * @return The message.
     */
    protected abstract ChunkUpdatableMessage showMessage(int offset);

    /**
     * Creates a {@link GameMessageWriter} that hides this entity.
     *
     * @param offset The chunk offset.
     * @return The message.
     */
    protected abstract ChunkUpdatableMessage hideMessage(int offset);

    /**
     * Sends a packet to all applicable players to display this entity.
     * <strong>This does NOT register the entity, it just makes it visible to the owner. It won't be
     * interactable.</strong>
     * Use functions in {@link World} to register entities.
     */
    public final void show() {
        if (hidden) {
            int offset = getChunk().offset(position);
            chunkRepository.queueUpdate(new ChunkUpdatableRequest(this, showMessage(offset), true));
            hidden = false;
        }
    }

    /**
     * Sends a packet to all applicable players to hide this entity.
     * <strong>This does NOT unregister the entity, it just makes it invisible to the owner.</strong>
     * Use functions in {@link World} to unregister entities.
     */
    public final void hide() {
        if (!hidden) {
            int offset = getChunk().offset(position);
            chunkRepository.removeUpdate(this);
            chunkRepository.queueUpdate(new ChunkUpdatableRequest(this, hideMessage(offset), false));
            hidden = true;
        }
    }

    /**
     * Determines if this entity is visible to {@code player}.
     *
     * @param player The player.
     * @return {@code true} if this item is visible to the player.
     */
    public final boolean isVisibleTo(Player player) {
        if (!player.isViewableFrom(this)) {
            return false;
        }
        return isGlobal() || view.isViewableFor(player);
    }

    /**
     * @return Who this entity can be viewed by.
     */
    public final ChunkUpdatableView getView() {
        return view;
    }

    /**
     * @return {@code true} if this entity is visible for everyone.
     */
    public final boolean isGlobal() {
        return view.isGlobal();
    }

    /**
     * @return {@code true} if this entity is visible for specific players.
     */
    public final boolean isLocal() {
        return !isGlobal();
    }

    /**
     * @return {@code true} if this entity is invisible, {@code false} otherwise.
     */
    public boolean isHidden() {
        return hidden;
    }

    /**
     * Sets the hidden flag.
     */
    protected void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    /**
     * Returns an {@link ImmutableList} representing surrounding players. Each set represents players within a viewable
     * chunk.
     * <p>
     * We retain references to the original sets instead of flattening them, so that they implicitly stay updated as
     * players move in and out of view of this entity. This means we only have to build the returned list once.
     */
    public final ImmutableList<Set<Player>> getSurroundingPlayers() { //todo test
        if (surroundingPlayers == null) {
            ImmutableList.Builder<Set<Player>> builder = ImmutableList.builder();
            // Retrieve viewable chunks.
            Set<ChunkRepository> viewableChunks = world.getChunks().getViewableChunks(position);
            for (ChunkRepository chunk : viewableChunks) {
                // Wrap players in immutable view, add it.
                Set<Player> players = Collections.unmodifiableSet(chunk.getAll(EntityType.PLAYER));
                builder.add(players);
            }
            surroundingPlayers = builder.build();
        }
        return surroundingPlayers;
    }
}