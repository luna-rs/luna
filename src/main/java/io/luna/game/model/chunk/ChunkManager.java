package io.luna.game.model.chunk;

import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.StationaryEntity;
import io.luna.game.model.World;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.ClearChunkMessageWriter;
import io.luna.net.msg.out.GroupedEntityMessageWriter;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A model that loads new chunks and manages loaded chunks.
 *
 * @author lare96
 */
public final class ChunkManager implements Iterable<ChunkRepository> {
// todo clean up, redesign, remove unused functions
    /**
     * Determines the local player count at which prioritized updating will start.
     */
    public static final int LOCAL_MOB_THRESHOLD = 50;

    /**
     * How many layers of chunks will be loaded around a player, when looking for viewable entities.
     */
    public static final int RADIUS = 3;

    /**
     * A map of loaded chunks.
     */
    private final Map<Chunk, ChunkRepository> chunks = new HashMap<>(29_278);

    /**
     * A queue of chunks that updates were sent to.
     */
    private final Queue<ChunkRepository> updatedChunks = new ArrayDeque<>();

    /**
     * The world instance.
     */
    private final World world;

    /**
     * Creates a new {@link ChunkManager}.
     *
     * @param world The world instance.
     */
    public ChunkManager(World world) {
        this.world = world;
    }

    @Override
    public Spliterator<ChunkRepository> spliterator() {
        return Spliterators.spliterator(chunks.values(), Spliterator.NONNULL);
    }

    @Override
    public Iterator<ChunkRepository> iterator() {
        return chunks.values().iterator();
    }

    /**
     * Retrieves a chunk based on the argued chunk position, constructing and loading a new one if needed.
     *
     * @param position The position to construct a new chunk with.
     * @return The existing or newly loaded chunk.
     */
    public ChunkRepository load(Chunk position) {
        return chunks.computeIfAbsent(position, key -> new ChunkRepository(world, key));
    }

    /**
     * Retrieves a chunk based on the argued position, constructing and loading a new one if needed.
     *
     * @param position The position to construct a new chunk with.
     * @return The existing or newly loaded chunk.
     */
    public ChunkRepository load(Position position) {
        return load(position.getChunk());
    }

    /**
     * Shortcut to {@link #getUpdateMobs(Player, EntityType)} for type {@code PLAYER}.
     */
    public Set<Player> getUpdatePlayers(Player player) {
        return getUpdateMobs(player, EntityType.PLAYER);
    }

    /**
     * Shortcut to {@link #getUpdateMobs(Player, EntityType)} for type {@code NPC}.
     */
    public Set<Npc> getUpdateNpcs(Player player) {
        return getUpdateMobs(player, EntityType.NPC);
    }

    /**
     * Returns an update set for {@code type}, potentially sorted by the {@link ChunkMobComparator}.
     *
     * @param player The player.
     * @param type The entity type.
     * @param <T> The type.
     * @return The update set.
     */
    private <T extends Mob> Set<T> getUpdateMobs(Player player, EntityType type) {
        Set<T> updateSet;
        if (player.getLocalPlayers().size() > LOCAL_MOB_THRESHOLD && type == EntityType.PLAYER ||
                player.getLocalNpcs().size() > LOCAL_MOB_THRESHOLD && type == EntityType.NPC) {
            updateSet = new TreeSet<>(new ChunkMobComparator(player));
        } else {
            updateSet = new HashSet<>();
        }

        var chunkPosition = new Chunk(player.getPosition());
        for (int x = -RADIUS; x < RADIUS; x++) {
            for (int y = -RADIUS; y < RADIUS; y++) {
                var currentChunk = load(chunkPosition.translate(x, y));
                Set<T> mobs = currentChunk.getAll(type);
                for (T inside : mobs) {
                    if (inside.isViewableFrom(player)) {
                        updateSet.add(inside);
                    }
                }
            }
        }
        return updateSet;
    }

    /**
     * Returns a set of viewable entities.
     *
     * @param position The relative position.
     * @param type The entity type.
     * @param <T> The type.
     * @return The set.
     */
    public <T extends Entity> Set<T> getViewableEntities(Position position, EntityType type) {
        Set<T> viewable = new HashSet<>();
        Chunk chunkPos = position.getChunk();
        for (int x = -RADIUS; x < RADIUS; x++) {
            for (int y = -RADIUS; y < RADIUS; y++) {
                ChunkRepository chunkRepository = load(chunkPos.translate(x, y));
                Set<T> entities = chunkRepository.getAll(type);
                for (T inside : entities) {
                    if (inside.getPosition().isViewable(position)) {
                        viewable.add(inside);
                    }
                }
            }
        }
        return viewable;
    }


    /**
     * Returns a list of viewable chunks.
     *
     * @param position The relative position.
     * @return The list.
     */
    public Set<ChunkRepository> getViewableChunks(Position position) {
        Set<ChunkRepository> viewable = new HashSet<>(16);
        Chunk chunkPos = position.getChunk();
        for (int x = -RADIUS; x < RADIUS; x++) {
            for (int y = -RADIUS; y < RADIUS; y++) {
                ChunkRepository chunkRepository = load(chunkPos.translate(x, y));
                viewable.add(chunkRepository);
            }
        }
        return viewable;
    }

    /**
     * Refreshes {@link StationaryEntity}s within all viewable chunks of {@code player}.
     *
     * @param player The player.
     */
    public void sendUpdates(Player player, Position oldPosition, boolean fullRefresh) {
        Set<ChunkRepository> oldChunks = getViewableChunks(oldPosition);
        Set<ChunkRepository> newChunks = getViewableChunks(player.getPosition());
        Set<ChunkRepository> viewableOldChunks = new HashSet<>();

        if (!fullRefresh) {
            // Don't need a full refresh, partial update of old viewable chunks. Only do a full update
            // of new chunks.
            viewableOldChunks.addAll(newChunks);
            viewableOldChunks.retainAll(oldChunks);

            newChunks.removeAll(oldChunks);
        }

        // Send out grouped entity updates for old chunks that still remain in view.
        for (ChunkRepository chunk : viewableOldChunks) {
            List<ChunkUpdatableMessage> updates = chunk.getUpdates(player);
            if (!updates.isEmpty()) {
                updatedChunks.add(chunk);
                player.queue(new GroupedEntityMessageWriter(player.getLastRegion(), chunk, updates));
            }
        }

        // Send out grouped entity updates for new chunks in view.
        for (ChunkRepository chunk : newChunks) {
            List<ChunkUpdatableMessage> updates = chunk.getUpdates(player);
            // Chunk is being cleared, so resend static updates like displaying registered objects and items.
            for(ChunkUpdatableRequest request : chunk.getPersistentUpdates()) {
                ChunkUpdatableView view = request.getUpdatable().computeCurrentView();
                if (view.isViewableFor(player)) {
                    updates.add(request.getMessage());
                }
            }
            if (!updates.isEmpty()) {
                updatedChunks.add(chunk);
                player.queue(new ClearChunkMessageWriter(player.getLastRegion(), chunk));
                player.queue(new GroupedEntityMessageWriter(player.getLastRegion(), chunk, updates));
            }
        }
    }

    /**
     * Resets the list of pending updates for all updated chunks.
     */
    public void resetUpdatedChunks() {
        for (; ; ) {
            ChunkRepository chunk = updatedChunks.poll();
            if (chunk == null) {
                break;
            }
            chunk.resetUpdates();
        }
    }

    /**
     * @return All chunks being managed by this repository.
     */
    public Collection<ChunkRepository> getAll() {
        return Collections.unmodifiableCollection(chunks.values());
    }

    /**
     * @return A stream over every single chunk.
     */
    public Stream<ChunkRepository> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
