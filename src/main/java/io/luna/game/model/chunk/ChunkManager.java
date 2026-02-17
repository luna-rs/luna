package io.luna.game.model.chunk;

import io.luna.game.model.Position;
import io.luna.game.model.World;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.ClearChunkMessageWriter;
import io.luna.net.msg.out.GroupedEntityMessageWriter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Loads and manages {@link ChunkRepository} instances for the world, and drives per-player chunk update dispatch.
 * <p>
 * A {@link ChunkRepository} is created lazily on demand via {@link #load(Chunk)} / {@link #load(Position)} and then
 * retained in {@link #repositories}. Each repository holds the entities and queued update requests for its chunk.
 * <p>
 * <b>View radius:</b> This manager defines a square "viewable" area around a base chunk. The current implementation
 * iterates from {@code -VIEWABLE_RADIUS} (inclusive) to {@code VIEWABLE_RADIUS} (inclusive), producing a symmetric
 * inclusive radius.
 * <p>
 * <b>Update dispatch:</b> {@link #sendUpdates(Player, Position, boolean)} compares the chunks around the player's old
 * and new positions and:
 * <ul>
 *     <li>sends grouped updates for chunks that remain visible</li>
 *     <li>sends grouped updates + persistent replays for chunks newly entering view</li>
 * </ul>
 * <p>
 * <b>Reset flow:</b> Any chunk that was sent updates is tracked in {@link #updated}. After all players have been
 * processed for the tick, callers should invoke {@link #resetUpdatedChunks()} to drain/clear temporary requests and
 * promote any persistent requests in those chunks.
 *
 * @author lare96
 */
public final class ChunkManager implements Iterable<ChunkRepository> {

    /**
     * How many "layers" of chunks are considered viewable around a base chunk.
     */
    public static final int VIEWABLE_RADIUS = 3;

    /**
     * Loaded chunk repositories keyed by {@link Chunk}.
     */
    private final Map<Chunk, ChunkRepository> repositories = new ConcurrentHashMap<>(29_278);

    /**
     * Chunks that have had updates sent this tick and therefore must be reset in {@link #resetUpdatedChunks()}.
     * <p>
     * Using a {@link Set} prevents duplicate resets of the same chunk when multiple players observe it in one tick.
     */
    private final Set<ChunkRepository> updated = new HashSet<>();

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
        return Spliterators.spliterator(repositories.values(), Spliterator.NONNULL);
    }

    @Override
    public Iterator<ChunkRepository> iterator() {
        return repositories.values().iterator();
    }

    /**
     * Loads (or retrieves) the {@link ChunkRepository} for {@code chunk}.
     * <p>
     * Repositories are created lazily and then cached.
     *
     * @param chunk The chunk to load.
     * @return The existing or newly created repository.
     */
    public ChunkRepository load(Chunk chunk) {
        return repositories.computeIfAbsent(chunk, key -> new ChunkRepository(world, key));
    }

    /**
     * Loads (or retrieves) the {@link ChunkRepository} for the chunk containing {@code position}.
     *
     * @param position The position whose chunk repository should be loaded.
     * @return The existing or newly created repository.
     */
    public ChunkRepository load(Position position) {
        return load(position.getChunk());
    }

    /**
     * Finds nearby mobs of {@code type} that should be considered for updates relative to {@code player}.
     * <p>
     * This is a convenience wrapper around the world-level query API and typically filters by whether each mob
     * is viewable from {@code player}.
     *
     * @param player The player requesting an update set.
     * @param type The mob subtype class to search for.
     * @param <T> The mob subtype.
     * @return A list of matching mobs.
     */
    public <T extends Mob> Collection<T> findUpdateMobs(Player player, Class<T> type) {
        return world.find(
                player.getPosition(),
                type,
                () -> new ArrayList<>(255),
                entity -> entity.isViewableFrom(player),
                Position.VIEWING_DISTANCE
        );
    }

    /**
     * Computes the repositories for chunks in the viewable area around {@code base}.
     * <p>
     * The current implementation returns a list in deterministic nested-loop order. Repositories are loaded
     * as a side effect of this call.
     *
     * @param base The base position.
     * @return A list of repositories surrounding {@code base}'s chunk.
     */
    public List<ChunkRepository> findViewableChunks(Position base) {
        Chunk chunk = base.getChunk();
        List<ChunkRepository> viewable = new ArrayList<>(16);
        for (int x = -VIEWABLE_RADIUS; x <= VIEWABLE_RADIUS; x++) {
            for (int y = -VIEWABLE_RADIUS; y <= VIEWABLE_RADIUS; y++) {
                ChunkRepository repository = load(chunk.translate(x, y));
                viewable.add(repository);
            }
        }
        return viewable;
    }

    /**
     * Sends chunk update messages for {@code player} based on movement and refresh mode.
     * <p>
     * This method computes:
     * <ul>
     *     <li>chunks that remain in view</li>
     *     <li>chunks newly entering view</li>
     * </ul>
     * <p>
     * For chunks remaining in view, only the current tick's queued updates are sent. For chunks newly entering
     * view, persistent updates are replayed in addition to current tick updates.
     * <p>
     * Any chunk that has updates sent is tracked in {@link #updated} so the caller can later invoke
     * {@link #resetUpdatedChunks()} once per tick.
     *
     * @param player The player to send updates to.
     * @param oldPosition The player's previous position.
     * @param fullRefresh If {@code true}, treat all viewable chunks as "new" (forces full resend).
     */
    public void sendUpdates(Player player, Position oldPosition, boolean fullRefresh) {
        List<ChunkRepository> oldChunks = findViewableChunks(oldPosition);
        List<ChunkRepository> newChunks = findViewableChunks(player.getPosition());
        List<ChunkRepository> viewableOldChunks = new ArrayList<>();

        if (!fullRefresh) {
            // Chunks still in view: new ∩ old.
            viewableOldChunks.addAll(newChunks);
            viewableOldChunks.retainAll(oldChunks);

            // Truly new chunks: new - old.
            newChunks.removeAll(oldChunks);
        }

        // Send grouped updates for chunks that remain in view.
        for (ChunkRepository chunk : viewableOldChunks) {
            List<ChunkUpdatableMessage> updates = chunk.getUpdates(player);
            if (!updates.isEmpty()) {
                updated.add(chunk);
                player.queue(new ClearChunkMessageWriter(player.getLastRegion(), chunk));
                player.queue(new GroupedEntityMessageWriter(player.getLastRegion(), chunk, updates));
            }
        }

        // Send grouped updates + persistent replays for newly viewable chunks.
        for (ChunkRepository chunk : newChunks) {
            List<ChunkUpdatableMessage> updates = chunk.getUpdates(player);

            // Replay persistent updates (objects/items/etc.) when the chunk is treated as "new" to the client.
            for (ChunkUpdatableRequest request : chunk.getPersistentUpdates()) {
                ChunkUpdatableView view = request.getUpdatable().computeCurrentView();
                if (view.isViewableFor(player)) {
                    updates.add(request.getMessage());
                }
            }

            if (!updates.isEmpty()) {
                updated.add(chunk);
                player.queue(new ClearChunkMessageWriter(player.getLastRegion(), chunk));
                player.queue(new GroupedEntityMessageWriter(player.getLastRegion(), chunk, updates));
            }
        }
    }

    /**
     * Resets queued update state for all chunks that were updated this tick.
     *
     * <p>
     * This should typically be called once per game tick after all players have been processed. It delegates to
     * {@link ChunkRepository#resetUpdates()} and then removes each chunk from the {@link #updated} set.
     */
    public void resetUpdatedChunks() {
        Iterator<ChunkRepository> it = updated.iterator();
        while (it.hasNext()) {
            ChunkRepository chunk = it.next();
            chunk.resetUpdates();
            it.remove();
        }
    }

    /**
     * @return An unmodifiable collection of all loaded repositories.
     */
    public Collection<ChunkRepository> getAll() {
        return Collections.unmodifiableCollection(repositories.values());
    }

    /**
     * @return A sequential stream over all loaded repositories.
     */
    public Stream<ChunkRepository> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
