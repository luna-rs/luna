package io.luna.game.model;

import io.luna.LunaContext;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.chunk.ChunkRepository;
import io.luna.game.model.chunk.ChunkUpdatable;
import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.chunk.ChunkUpdatableRequest;
import io.luna.game.model.chunk.ChunkUpdatableView;

/**
 * A non-registered {@link ChunkUpdatable} type that exists temporarily in the game world.
 *
 * @author lare96
 */
public abstract class LocalEntity implements ChunkUpdatable {

    /**
     * The context.
     */
    protected final LunaContext context;

    /**
     * The id.
     */
    protected final int id;

    /**
     * The type.
     */
    protected final EntityType type;

    /**
     * The position.
     */
    protected final Position position;

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
        this.context = context;
        this.id = id;
        this.type = type;
        this.position = position;
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
     * Displays this entity by sending an update request with {@link #displayMessage(int)}.
     */
    public final void display() {
        Chunk chunk = position.getChunk();
        ChunkUpdatableMessage msg = displayMessage(chunk.offset(position));

        ChunkRepository chunkRepository = context.getWorld().getChunks().load(chunk);
        chunkRepository.queueUpdate(new ChunkUpdatableRequest(this, msg, false));
    }

    /**
     * @return The context.
     */
    public LunaContext getContext() {
        return context;
    }

    /**
     * @return The id.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The type.
     */
    public EntityType getType() {
        return type;
    }

    /**
     * @return The position.
     */
    public Position getPosition() {
        return position;
    }

    /**
     * @return Who this entity is viewable for.
     */
    public ChunkUpdatableView getView() {
        return view;
    }

}
