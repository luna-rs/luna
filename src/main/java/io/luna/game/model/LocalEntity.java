package io.luna.game.model;

import io.luna.LunaContext;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.chunk.ChunkUpdatable;
import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.chunk.ChunkUpdatableRequest;
import io.luna.game.model.chunk.ChunkUpdatableView;

/**
 * A temporary {@link Entity} that cannot be interacted with (i.e. Area-based sounds, projectiles, graphics). Discarded
 * after being displayed within a {@link ChunkUpdatableRequest}.
 *
 * @author lare96
 */
public abstract class LocalEntity extends Entity implements ChunkUpdatable {

    /**
     * The id.
     */
    protected final int id;

    /**
     * Who this entity is viewable for.
     */
    protected final ChunkUpdatableView view;

    /**
     * Creates a new {@link LocalEntity}.
     *
     * @param context The context.
     * @param id The id.
     * @param type The type.
     * @param position The position.
     * @param view Who this entity is viewable for.
     */
    public LocalEntity(LunaContext context, int id, EntityType type, Position position, ChunkUpdatableView view) {
        super(context, position, type);
        this.id = id;
        this.view = view;
    }

    /**
     * The message to send to display this entity as a part of a {@link ChunkUpdatableRequest}.
     *
     * @param offset The position offset.
     * @return The game message.
     */
    public abstract ChunkUpdatableMessage displayMessage(int offset);

    @Override
    public ChunkUpdatableView computeCurrentView() {
        return view;
    }

    /**
     * Local entities rely solely on identity when compared because they lack an index.
     */
    @Override
    public final int hashCode() {
        return System.identityHashCode(this);
    }

    /**
     * Local entities rely solely on identity when compared because they lack an index.
     */
    @Override
    public final boolean equals(Object obj) {
        return this == obj;
    }

    /**
     * Local entities aren't interactable and don't have a size.
     */
    @Override
    public final int sizeX() {
        return 0;
    }

    /**
     * Local entities aren't interactable and don't have a size.
     */
    @Override
    public final int sizeY() {
        return 0;
    }

    /**
     * Displays this entity by sending an update request with {@link #displayMessage(int)}.
     */
    public final void display() {
        Chunk chunk = position.getChunk();
        ChunkUpdatableMessage msg = displayMessage(chunk.offset(position));

        setState(EntityState.ACTIVE);
        chunkRepository.queueUpdate(new ChunkUpdatableRequest(this, msg, false));
    }

    /**
     * @return The id.
     */
    public int getId() {
        return id;
    }

    /**
     * @return Who this entity is viewable for.
     */
    public ChunkUpdatableView getView() {
        return view;
    }

}
