package io.luna.game.model;

import com.google.common.collect.ImmutableList;
import io.luna.LunaContext;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessageWriter;
import io.luna.net.msg.out.ChunkPlacementMessageWriter;

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
    public enum UpdateType {
        SHOW, HIDE
    }

    /**
     * The player to update for. If empty, updates for all players.
     */
    private final Optional<Player> owner;

    /**
     * The position used for placement.
     */
    private final Position placement;

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
     * @param owner The player to update for. If empty, updates for all players.
     */
    public StationaryEntity(LunaContext context, Position position, EntityType type, Optional<Player> owner) {
        super(context, position, type);
        this.owner = owner;
        placement = position;//new Position(getChunkPosition().getAbsX(), getChunkPosition().getAbsY());
    }

    /**
     * Stationary entities rely solely on identity when compared because entities in chunks are held in a HashSet
     * datatype.
     * <br><br>
     * Weird issues can occur with a equals/hashcode implementation that is too strict, and there isn't much use in
     * having an implementation that is lenient.
     */
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    /**
     * Stationary entities rely solely on identity when compared because entities in chunks are held in a HashSet
     * datatype.
     * <br><br>
     * Weird issues can occur with a equals/hashcode implementation that is too strict.
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
     * <strong>This does NOT register the entity, so it cannot be interacted with by a Player.</strong>
     * Use functions in {@link World} to register entities.
     */
    public final void show() {
        if (hidden) {
            applyUpdate(UpdateType.SHOW);
        }
    }

    /**
     * Sends a packet to all applicable players to hide this entity.
     * <strong>This does NOT unregister the entity, it just makes it invisible to players.</strong>
     * Use functions in {@link World} to unregister entities.
     */
    public final void hide() {
        if (!hidden) {
            applyUpdate(UpdateType.HIDE);
        }
    }

    /**
     * Updates this entity, either locally or globally.
     *
     * @param updateType The update type to apply.
     */
    private void applyUpdate(UpdateType updateType) {
        if (owner.isPresent() && owner.get().isViewableFrom(this)) {
            // We have a player to update for.
            sendUpdateMessage(owner.get(), updateType);
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
    public void sendUpdateMessage(Player player, UpdateType updateType) {
        player.queue(new ChunkPlacementMessageWriter(placement));

        int offset = getChunkPosition().offset(position);
        if (updateType == UpdateType.SHOW) {
            player.queue(showMessage(offset));
            hidden = false;
        } else if (updateType == UpdateType.HIDE) {
            player.queue(hideMessage(offset));
            hidden = true;
        }
    }

    /**
     * Determines if this item is visible to {@code player}.
     *
     * @param player The player.
     * @return {@code true} if this item is visible to the player.
     */
    public boolean isVisibleTo(Player player) {
        if (!player.isViewableFrom(this)) {
            return false;
        }
        return isGlobal() || owner.filter(plrOwner -> plrOwner.equals(player)).isPresent();
    }

    /**
     * @return The player to update for.
     */
    public final Optional<Player> getOwner() {
        return owner;
    }

    /**
     * @return The player to update for, or {@code null}.
     */
    public final Player getOwnerInstance() {
        return owner.orElse(null);
    }

    /**
     * @return {@code true} if this entity is visible for everyone.
     */
    public final boolean isGlobal() {
        return owner.isEmpty();
    }

    /**
     * @return {@code true} if this entity is visible for just one player.
     */
    public final boolean isLocal() {
        return owner.isPresent();
    }

    /**
     * @return {@code true} if this entity is invisible, {@code false} otherwise.
     */
    public boolean isHidden() {
        return hidden;
    }

    /**
     * Returns an {@link ImmutableList} representing surrounding players. Each set represents players within a viewable
     * chunk.
     * <p>
     * We retain references to the original sets instead of flattening them, so that they implicitly stay updated as
     * players move in and out of view of this entity. This means we only have to build the returned list once.
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