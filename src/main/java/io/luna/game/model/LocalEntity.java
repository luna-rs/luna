package io.luna.game.model;

import io.luna.LunaContext;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.chunk.ChunkUpdatable;
import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.chunk.ChunkUpdatableRequest;
import io.luna.game.model.chunk.ChunkUpdatableView;

/**
 * A lightweight, non-interactable {@link Entity} that exists only to emit a one-off (or short-lived) chunk update
 * to nearby players.
 * <p>
 * Local entities are used for effects that are "rendered" client-side but are not part of the persistent world
 * model (e.g., area sounds, spot graphics, projectiles, temporary visuals).
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>Constructed with a {@link ChunkUpdatableView} describing who can see it.</li>
 *   <li>{@link #display()} queues a {@link ChunkUpdatableRequest} for its current {@link Chunk}.</li>
 *   <li>Once displayed, it transitions to {@link EntityState#ACTIVE} and will not re-display.</li>
 * </ul>
 * <p>
 * Because local entities do not have a stable world index (unlike players/npcs), equality is identity-based ({@code ==}).
 *
 * @author lare96
 */
public abstract class LocalEntity extends Entity implements ChunkUpdatable {

    /**
     * The effect/object id interpreted by the concrete local-entity type.
     */
    protected final int id;

    /**
     * The visibility rules determining which players receive the update for this entity.
     */
    protected final ChunkUpdatableView view;

    /**
     * Creates a new {@link LocalEntity}.
     *
     * @param context The game context.
     * @param id The local-entity id (meaning is defined by the implementation).
     * @param type The entity type classification.
     * @param position The world position this local entity is anchored to.
     * @param view Who this entity is viewable for (who should receive the update).
     */
    public LocalEntity(LunaContext context, int id, EntityType type, Position position, ChunkUpdatableView view) {
        super(context, position, type);
        this.id = id;
        this.view = view;
    }

    /**
     * Builds the message used to render this local entity as part of a {@link ChunkUpdatableRequest}.
     * <p>
     * The {@code offset} is a packed local position inside the chunk, provided by {@link Chunk#offset(Position)}.
     *
     * @param offset The chunk-local position offset for {@link #getPosition()}.
     * @return The outbound update message that causes the client to display this entity/effect.
     */
    public abstract ChunkUpdatableMessage displayMessage(int offset);

    @Override
    public ChunkUpdatableView computeCurrentView() {
        return view;
    }

    /**
     * Local entities rely solely on object identity for hashing since they do not have a stable index.
     */
    @Override
    public final int hashCode() {
        return System.identityHashCode(this);
    }

    /**
     * Local entities rely solely on object identity for equality since they do not have a stable index.
     */
    @Override
    public final boolean equals(Object obj) {
        return this == obj;
    }

    /**
     * Local entities are not interactable and do not occupy tiles for collision/size purposes.
     */
    @Override
    public final int sizeX() {
        return 0;
    }

    /**
     * Local entities are not interactable and do not occupy tiles for collision/size purposes.
     */
    @Override
    public final int sizeY() {
        return 0;
    }

    /**
     * Queues a chunk update to display this local entity to viewers determined by {@link #getView()}.
     * <p>
     * This method is single-use: once the entity has been displayed (i.e., state becomes {@link EntityState#ACTIVE}),
     * subsequent calls do nothing.
     */
    public final void display() {
        if (state != EntityState.ACTIVE) {
            Chunk chunk = position.getChunk();
            ChunkUpdatableMessage msg = displayMessage(chunk.offset(position));

            setState(EntityState.ACTIVE);
            chunkRepository.queueUpdate(new ChunkUpdatableRequest(this, msg, false));
        }
    }

    /**
     * @return The local entity id.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The visibility rules determining who receives the update for this entity.
     */
    public ChunkUpdatableView getView() {
        return view;
    }
}
