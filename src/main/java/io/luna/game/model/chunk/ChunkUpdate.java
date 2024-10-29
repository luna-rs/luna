package io.luna.game.model.chunk;

import io.luna.game.model.Entity;
import io.luna.game.model.StationaryEntity;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessageWriter;

import java.util.Optional;

/**
 * Represents a pending update for a {@link StationaryEntity} within a {@link Chunk}.
 *
 * @author lare96
 */
public final class ChunkUpdate {

    /**
     * The {@link StationaryEntity} this update is for.
     */
    private final Entity entity;

    /**
     * The player to update for.
     */
    private final Optional<Player> owner;

    /**
     * The update message.
     */
    private final GameMessageWriter message;

    /**
     * Creates a new {@link ChunkUpdate}.
     *
     * @param entity The {@link StationaryEntity} this update is for.
     * @param owner The player to update for.
     * @param message The update message.
     */
    public ChunkUpdate(Entity entity, Optional<Player> owner, GameMessageWriter message) {
        this.entity = entity;
        this.owner = owner;
        this.message = message;
    }

    /**
     * @return The {@link StationaryEntity} this update is for.
     */
    public Entity getEntity() {
        return entity;
    }

    /**
     * @return The player to update for.
     */
    public Optional<Player> getOwner() {
        return owner;
    }

    /**
     * @return The update message.
     */
    public GameMessageWriter getMessage() {
        return message;
    }
}
