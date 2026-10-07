package io.luna.game.model.chunk;

import io.luna.game.model.Position;
import io.luna.game.model.World;
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
 * and new positions, limited to those inside the map the client has loaded (see
 * {@link #findViewableChunks(Position, Position)}), and:
 * <ul>
 *     <li>Sends grouped updates for chunks that remain visible.</li>
 *     <li>Sends grouped updates + persistent replays for chunks newly entering view.</li>
 * </ul>
 * <p>
 * <b>Reset flow:</b> Any chunk that had updates queued is tracked in {@link #updated}, whether or not a player received
 * them. After all players have been processed for the tick, callers should invoke {@link #resetUpdatedChunks()} to
 * drain/clear temporary requests and promote any persistent requests in those chunks.
 *
 * @author lare96
 */
public final class ChunkManager implements Iterable<ChunkRepository> {

    /**
     * How many "layers" of chunks are considered viewable around a base chunk.
     */
    public static final int VIEWABLE_RADIUS = 3;

    /**
     * How many chunks the map the client has loaded extends from the chunk the region was centered on. The loaded map is
     * {@code 2 * CLIENT_REGION_RADIUS + 1} chunks (104 tiles) wide and tall.
     */
    public static final int CLIENT_REGION_RADIUS = 6;

    /**
     * Loaded chunk repositories keyed by {@link Chunk}.
     */
    private final Map<Chunk, ChunkRepository> repositories = new ConcurrentHashMap<>(29_278);

    /**
     * Chunks that have had updates queued this tick and therefore must be reset in {@link #resetUpdatedChunks()}.
     * <p>
     * Using a {@link Set} prevents duplicate resets of the same chunk when several updates are queued in one tick.
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
        return loadChunks(chunk.getX() - VIEWABLE_RADIUS, chunk.getX() + VIEWABLE_RADIUS,
                chunk.getY() - VIEWABLE_RADIUS, chunk.getY() + VIEWABLE_RADIUS);
    }

    /**
     * Computes the repositories for chunks in the viewable area around {@code base} that the client can address.
     * <p>
     * This is {@link #findViewableChunks(Position)} clamped to the map the client loaded for {@code region}. Chunk
     * update packets encode their chunk relative to the region base, and the client indexes its ground item and object
     * arrays with it, so a chunk outside the loaded map crashes the client. {@link Player#needsRegionUpdate()} only
     * re-bases the region once the player is within two chunks of the map's edge, so the view radius can reach a chunk
     * past it. A region change always forces a full refresh, so skipping those chunks loses nothing.
     * <p>
     * Repositories are only loaded for the chunks that are returned.
     *
     * @param base The base position.
     * @param region The region base the client was last sent.
     * @return A list of repositories surrounding {@code base}'s chunk that lie inside the client's loaded map.
     */
    public List<ChunkRepository> findViewableChunks(Position base, Position region) {
        Chunk chunk = base.getChunk();
        Chunk regionChunk = region.getChunk();
        return loadChunks(Math.max(chunk.getX() - VIEWABLE_RADIUS, regionChunk.getX() - CLIENT_REGION_RADIUS),
                Math.min(chunk.getX() + VIEWABLE_RADIUS, regionChunk.getX() + CLIENT_REGION_RADIUS),
                Math.max(chunk.getY() - VIEWABLE_RADIUS, regionChunk.getY() - CLIENT_REGION_RADIUS),
                Math.min(chunk.getY() + VIEWABLE_RADIUS, regionChunk.getY() + CLIENT_REGION_RADIUS));
    }

    /**
     * Loads the repositories for every chunk in the inclusive chunk-space range, in deterministic nested-loop order.
     * The list is empty if either range is empty.
     *
     * @param minX The lowest chunk x coordinate.
     * @param maxX The highest chunk x coordinate.
     * @param minY The lowest chunk y coordinate.
     * @param maxY The highest chunk y coordinate.
     * @return The loaded repositories.
     */
    private List<ChunkRepository> loadChunks(int minX, int maxX, int minY, int maxY) {
        List<ChunkRepository> chunks = new ArrayList<>(Math.max(0, maxX - minX + 1) * Math.max(0, maxY - minY + 1));
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                chunks.add(load(new Chunk(x, y)));
            }
        }
        return chunks;
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
     * view, persistent updates are replayed first, followed by current tick updates.
     *
     * @param player The player to send updates to.
     * @param oldPosition The player's previous position.
     * @param fullRefresh If {@code true}, treat all viewable chunks as "new" (forces full resend).
     */
    public void sendUpdates(Player player, Position oldPosition, boolean fullRefresh) {
        Position lastRegion = player.getLastRegion();
        List<ChunkRepository> oldChunks = findViewableChunks(oldPosition, lastRegion);
        List<ChunkRepository> newChunks = findViewableChunks(player.getPosition(), lastRegion);
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
                player.queue(new GroupedEntityMessageWriter(player.getLastRegion(), chunk, updates));
            }
        }

        // Send grouped updates + persistent replays for newly viewable chunks.
        for (ChunkRepository chunk : newChunks) {
            List<ChunkUpdatableMessage> updates = new ArrayList<>();

            // Replay persistent updates (objects/items/etc.) when the chunk is treated as "new" to the client.
            for (ChunkUpdatableRequest request : chunk.getPersistentUpdates()) {
                if (ChunkRepository.isViewableFor(request, player)) {
                    updates.add(request.getMessage());
                }
            }

            // This tick's updates are newer than the persistent ones, so they go last.
            updates.addAll(chunk.getUpdates(player));

            if (!updates.isEmpty()) {
                player.queue(new ClearChunkMessageWriter(player.getLastRegion(), chunk));
                player.queue(new GroupedEntityMessageWriter(player.getLastRegion(), chunk, updates));
            }
        }
    }

    /**
     * Marks {@code chunk} to be reset in {@link #resetUpdatedChunks()}.
     *
     * @param chunk The chunk that had an update queued.
     */
    void markUpdated(ChunkRepository chunk) {
        updated.add(chunk);
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
