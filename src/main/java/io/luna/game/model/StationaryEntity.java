package io.luna.game.model;

import com.google.common.collect.ImmutableList;
import io.luna.LunaContext;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.chunk.ChunkPosition;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessageWriter;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * An abstraction model representing non-moving {@link Entity}.
 *
 * @author lare96 <http://github.com/lare96>
 */
public abstract class StationaryEntity extends Entity {

    /**
     * An enumerated type whose elements represent either a show or hide update.
     */
    private enum UpdateType {
        SHOW, HIDE
    }

    /**
     * The player to update for. If empty, updates for all players.
     */
    private final Optional<Player> player;

    /**
     * The surrounding players. Initialized lazily, use {@link #getSurroundingPlayers()}.
     */
    private ImmutableList<Set<Player>> surroundingPlayers;

    /**
     * Creates a new local {@link StationaryEntity}.
     *
     * @param context The context instance.
     * @param position The position.
     * @param type The entity type.
     * @param player The player to update for. If empty, updates for all players.
     */
    public StationaryEntity(LunaContext context, Position position, EntityType type, Optional<Player> player) {
        super(context, position, type);
        this.player = player;
    }

    /**
     * Creates a {@link GameMessageWriter} that shows this entity.
     *
     * @param offset The chunk offset.
     * @return The message.
     */
    protected abstract GameMessageWriter showMessage(int offset);

    /**
     * Creates a {@link GameMessageWriter} that hides this entity.
     *
     * @param offset The chunk offset.
     * @return The message.
     */
    protected abstract GameMessageWriter hideMessage(int offset);

    /**
     * Sends a packet to all applicable players to display this entity.
     */
    public final void show() {
        applyUpdate(UpdateType.SHOW);
    }

    /**
     * Sends a packet to all applicable players to hide this entity.
     */
    public final void hide() {
        applyUpdate(UpdateType.HIDE);
    }

    /**
     * Updates this entity, either locally or globally.
     *
     * @param updateType The update type to apply.
     */
    private void applyUpdate(UpdateType updateType) {
        if (player.isPresent() && player.get().isViewableFrom(this)) {

            // We have a player to update for.
            sendUpdateMessage(player.get(), updateType);
        } else {
            // We don't, so update for all viewable surrounding players.
            for (Set<Player> chunkPlayers : getSurroundingPlayers()) {
                for (Player inside : chunkPlayers) {
                    if (isViewableFrom(inside)) {
                        sendUpdateMessage(inside, updateType);
                    }
                }
            }
        }
    }

    /**
     * Sends an update message to {@code player}.
     *
     * @param player The player.
     * @param updateType The update type to apply.
     */
    private void sendUpdateMessage(Player player, UpdateType updateType) {
        ChunkPosition chunkPosition = player.getChunkPosition();
        int offset = chunkPosition.offset(position);
        if (updateType == UpdateType.SHOW) {
            player.queue(showMessage(offset));
        } else if (updateType == UpdateType.HIDE) {
            player.queue(hideMessage(offset));
        }
    }

    /**
     * @return The player to update for.
     */
    public final Optional<Player> getPlayer() {
        return player;
    }

    /**
     * @return {@code true} if this entity is updating for everyone.
     */
    public final boolean isGlobal() {
        return !player.isPresent();
    }

    /**
     * Returns an {@link ImmutableList} representing surrounding players. Each set represents players within a viewable
     * chunk.
     * <p>
     * We retain references to the original sets instead of flattening them, so that they implicitly stay updated as
     * players move in and out of view of this entity.
     */
    public final ImmutableList<Set<Player>> getSurroundingPlayers() {
        if (surroundingPlayers == null) {
            ImmutableList.Builder<Set<Player>> builder = ImmutableList.builder();
            // Retrieve viewable chunks.
            List<Chunk> viewableChunks = world.getChunks().getViewableChunks(position);
            for (Chunk chunk : viewableChunks) {
                // Wrap players in immutable view, add it.
                Set<Player> players = Collections.unmodifiableSet(chunk.getAll(EntityType.PLAYER));
                builder.add(players);
            }
            surroundingPlayers = builder.build();
        }
        return surroundingPlayers;
    }
}