package io.luna.game.model.collision;

import io.luna.game.model.Position;
import io.luna.game.model.chunk.ChunkManager;
import io.luna.game.model.chunk.ChunkRepository;

import java.util.Arrays;

/**
 * A reader of the {@link CollisionFlag}s of tiles by their absolute coordinates.
 * <p>
 * Searches look at the same few chunks over and over, so a view remembers the matrices of the chunks it read last. A
 * view reads either the live matrices or the snapshots, and does not change which one for its whole life. A view is
 * not thread safe and is meant to be short lived: create one per search.
 *
 * @author hydrozoa
 */
public final class CollisionView {

    /**
     * The number of chunks a view remembers.
     */
    private static final int CACHE_SIZE = 16;

    /**
     * The chunk manager that chunks are loaded from.
     */
    private final ChunkManager chunks;

    /**
     * If the snapshots are read instead of the live matrices.
     */
    private final boolean safe;

    /**
     * The chunk coordinates of each remembered chunk, or {@code -1} if the slot is empty.
     */
    private final long[] keys = new long[CACHE_SIZE];

    /**
     * The matrices of each remembered chunk, one per level.
     */
    private final CollisionMatrix[][] matrices = new CollisionMatrix[CACHE_SIZE][];

    /**
     * Creates a new {@link CollisionView}.
     *
     * @param chunks The chunk manager to load chunks from.
     * @param safe {@code true} to read the snapshots, which is safe on any thread, or {@code false} to read the live
     * matrices, which is only safe on the game thread.
     */
    CollisionView(ChunkManager chunks, boolean safe) {
        this.chunks = chunks;
        this.safe = safe;
        Arrays.fill(keys, -1);
    }

    /**
     * Returns the {@link CollisionFlag}s of the tile at the given coordinates. Tiles outside of the world, and tiles
     * with no map data, have every flag set.
     *
     * @param x The absolute x coordinate.
     * @param y The absolute y coordinate.
     * @param level The height level.
     * @return The collision flags of the tile.
     */
    public int get(int x, int y, int level) {
        if (x < 0 || y < 0 || level < 0 || level >= matrixCount()) {
            return -1;
        }

        int chunkX = x >> 3;
        int chunkY = y >> 3;
        long key = ((long) chunkX << 32) | chunkY;
        int slot = (chunkX * 31 + chunkY) & (CACHE_SIZE - 1);
        if (keys[slot] != key) {
            ChunkRepository repository = chunks.load(new Position(x, y, level));
            matrices[slot] = safe ? repository.getSnapshot() : repository.getMatrices();
            keys[slot] = key;
        }
        return matrices[slot][level].get(x & 7, y & 7);
    }

    /**
     * Returns the number of height levels in the world.
     */
    private static int matrixCount() {
        return Position.HEIGHT_LEVELS.upperEndpoint();
    }
}
