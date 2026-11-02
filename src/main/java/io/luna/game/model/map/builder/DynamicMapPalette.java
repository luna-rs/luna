package io.luna.game.model.map.builder;

import io.luna.game.model.Region;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.map.DynamicMap;
import io.luna.game.model.map.builder.DynamicMapChunk.Rotation;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Represents a 13x13x4 space made up of {@link DynamicMapChunk} types. Chunks from the real Runescape world can
 * be copied into this palette in order to make {@link DynamicMap} types.
 *
 * @author lare96
 */
public final class DynamicMapPalette {
    /**
     * Retrieves all {@link Chunk} locations within {@code regionId}.
     *
     * @param regionId The ID of the region.
     * @return All chunks within the region
     */
    public static Set<Chunk> getAllChunksInRegion(int regionId) {
        Set<Chunk> chunks = new LinkedHashSet<>(64);
        Region region = new Region(regionId);
        Chunk baseChunk = region.getAbsPosition().getChunk();
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                chunks.add(baseChunk.translate(x, y));
            }
        }
        return chunks;
    }

    /**
     * Retrieves all {@link Chunk} locations within {@code radiusX} and {@code radiusY} surrounding {@code baseChunk}.
     *
     * @param baseChunk The base chunk.
     * @param chunkZ The source plane.
     * @param radiusX The {@code x} radius.
     * @param radiusY The {@code y} radius.
     * @return All chunks within the radius.
     */
    public static Set<Chunk> getSurroundingChunks(Chunk baseChunk, int radiusX, int radiusY) {
        checkArgument(radiusX >= 0 && radiusY >= 0, "Chunk radii cannot be negative.");
        Set<Chunk> chunks = new LinkedHashSet<>();
        for (int x = -radiusX; x <= radiusX; x++) {
            for (int y = -radiusY; y <= radiusY; y++) {
                Chunk nextChunk = baseChunk.translate(x, y);
                chunks.add(nextChunk);
            }
        }
        return chunks;
    }

    /**
     * A consumer used within {@link #forEach(PaletteConsumer)}.
     */
    public interface PaletteConsumer {

        /**
         * The application function of this consumer.
         *
         * @param x The {@code x} slot in the palette.
         * @param y The {@code y} slot in the palette.
         * @param z The {@code z} slot in the palette.
         */
        void apply(int x, int y, int z);
    }

    /**
     * The dynamic map palette.
     */
    private final DynamicMapChunk[][][] palette = new DynamicMapChunk[13][13][4];

    /**
     * Traverses through this palette applying {@code processor} to every index.
     *
     * @param processor The consumer to apply.
     */
    public void forEach(PaletteConsumer processor) {
        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 13; x++) {
                for (int y = 0; y < 13; y++) {
                    processor.apply(x, y, z);
                }
            }
        }
    }

    /**
     * Fills this palette at height level {@code z} with all chunks located within {@code region}.
     *
     * @param startXY Where on the palette to place the region (min 0, max 5).
     * @param z The height level on the palette to place the region.
     * @param regionZ The source region plane.
     * @param region The region to place.
     * @return This palette.
     */
    public DynamicMapPalette setRegion(int startXY, int z, Region region, int regionZ) {
        checkPlane(z);
        checkPlane(regionZ);
        checkArgument(startXY >= 0 && startXY <= 5, "startXY must be above or equal to 0 and below or equal to 5.");

        int regionId = region.getId();
        Queue<Chunk> regionChunks = new ArrayDeque<>(getAllChunksInRegion(regionId));
        for (int x = startXY; x < startXY + 8; x++) { // A region is 8x8 chunks, so we give it 8x8 slots on our palette.
            for (int y = startXY; y < startXY + 8; y++) {
                Chunk chunk = regionChunks.poll();
                checkState(chunk != null, "Size mismatch, expected 64 chunks in regionChunks.");
                palette[x][y][z] = new DynamicMapChunk(chunk, regionZ, Rotation.NORMAL);
            }
        }
        return this;
    }

    /**
     * Fills this palette at height level {@code z} with all chunks surrounding {@code baseChunk} with {@code radius}.
     *
     * @param startX The {@code x} placement coordinate on the palette.
     * @param startY The {@code y} placement coordinate on the palette.
     * @param z The height level on the palette to place the chunks.
     * @param baseChunk The base chunk.
     * @param chunkZ The source plane.
     * @param radiusX The {@code x} radius of the base chunk.
     * @param radiusY The {@code y} radius of the base chunk.
     * @return This palette.
     */
    public DynamicMapPalette setChunkRadius(int startX, int startY, int z, Chunk baseChunk, int chunkZ, int radiusX, int radiusY) {
        checkPlane(z);
        checkPlane(chunkZ);
        checkArgument(radiusX >= 0 && radiusX <= 6 && radiusY >= 0 && radiusY <= 6,
                "Chunk radii must be between 0 and 6.");
        int lowerBoundX = startX - radiusX;
        int upperBoundX = startX + radiusX;
        int lowerBoundY = startY - radiusY;
        int upperBoundY = startY + radiusY;
        checkArgument(lowerBoundX >= 0 && lowerBoundY >= 0, "[startX - radiusX && startY - radiusY] cannot be below 0");
        checkArgument(upperBoundX < palette.length && upperBoundY < palette.length, "[startX + radiusX && startY + radiusY] cannot exceed palette size");

        Queue<Chunk> regionChunks = new ArrayDeque<>(getSurroundingChunks(baseChunk, radiusX, radiusY));
        for (int x = startX - radiusX; x <= startX + radiusX; x++) {
            for (int y = startY - radiusY; y <= startY + radiusY; y++) {
                Chunk chunk = regionChunks.poll();
                checkState(chunk != null, "Size mismatch in regionChunks.");
                palette[x][y][z] = new DynamicMapChunk(chunk, chunkZ, Rotation.NORMAL);
            }
        }
        return this;
    }

    /**
     * Fills this palette with {@code regionId} at height level {@code 0}.
     *
     * @param regionId The region to fill the palette with.
     * @param regionZ The source plane.
     * @return This palette.
     */
    public DynamicMapPalette fillWithRegion(int regionId, int regionZ) {
        return fillWithRegion(new Region(regionId), regionZ);

    }

    /**
     * Fills this palette with {@code region} at height level {@code 0}.
     *
     * @param region The region to fill the palette with.
     * @param regionZ The source plane.
     * @return This palette.
     */
    public DynamicMapPalette fillWithRegion(Region region, int regionZ) {
        return setRegion(4, 0, region, regionZ);
    }

    /**
     * Fills this palette around destination slot (6, 6) on plane zero. The source center remains at
     * slot (6, 6), including at the maximum radius of six chunks.
     *
     * @param baseChunk The source center chunk.
     * @param chunkZ The source plane.
     * @param radius The radius, from zero through six.
     * @return This palette.
     */
    public DynamicMapPalette fillWithChunks(Chunk baseChunk, int chunkZ, int radius) {
        checkArgument(radius >= 0 && radius <= 6, "radius must be between 0 and 6");
        return setChunkRadius(6, 6, 0, baseChunk, chunkZ, radius, radius);
    }

    /**
     * Fills unassigned slots on one destination plane, retaining existing chunks.
     *
     * @param chunk The source chunk to repeat.
     * @param plane The destination plane, from 0 through 3.
     * @return This palette.
     */
    public DynamicMapPalette fillEmpty(DynamicMapChunk chunk, int plane) {
        checkPlane(plane);
        java.util.Objects.requireNonNull(chunk, "chunk");
        forEach(((x, y, z) -> {
            if (z == plane && palette[x][y][z] == null) {
                palette[x][y][z] = chunk;
            }
        }));
        return this;
    }

    /**
     * Sets the {@link DynamicMapChunk} on the specified coordinates in the palette.
     *
     * @param x The {@code x} coordinate.
     * @param y The {@code y} coordinate.
     * @param z The {@code z} coordinate.
     * @param chunk The immutable source placement, or null to clear this slot.
     * @return This palette.
     */
    public DynamicMapPalette setChunk(int x, int y, int z, DynamicMapChunk chunk) {
        checkSlot(x, y, z);
        palette[x][y][z] = chunk;
        return this;
    }

    /**
     * Retrieves the {@link DynamicMapChunk} on the specified coordinates in the palette.
     *
     * @param x The {@code x} coordinate.
     * @param y The {@code y} coordinate.
     * @param z The {@code z} coordinate.
     * @return The dynamic map chunk.
     */
    public DynamicMapChunk getChunk(int x, int y, int z) {
        checkSlot(x, y, z);
        return palette[x][y][z];
    }

    /**
     * @return An independent array snapshot of this palette.
     */
    public DynamicMapChunk[][][] getArray() {
        DynamicMapChunk[][][] copy = new DynamicMapChunk[13][13][4];
        forEach((x, y, z) -> copy[x][y][z] = palette[x][y][z]);
        return copy;
    }

    /**
     * Copies this layout so a built map cannot change when its builder is reused.
     *
     * @return An independent palette containing the same immutable chunks.
     */
    public DynamicMapPalette copy() {
        DynamicMapPalette copy = new DynamicMapPalette();
        forEach((x, y, z) -> copy.palette[x][y][z] = palette[x][y][z]);
        return copy;
    }

    /**
     * Validates a destination slot before accessing the backing array.
     */
    private static void checkSlot(int x, int y, int z) {
        checkArgument(x >= 0 && x < 13 && y >= 0 && y < 13, "Palette coordinates must be between 0 and 12.");
        checkPlane(z);
    }

    /**
     * Validates source and destination planes using the four-plane map format.
     */
    private static void checkPlane(int plane) {
        checkArgument(plane >= 0 && plane < 4, "Plane must be between 0 and 3.");
    }
}
