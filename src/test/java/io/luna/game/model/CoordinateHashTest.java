package io.luna.game.model;

import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.chunk.ChunkManager;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoordinateHashTest {
    @Test
    void positionKeysPreserveEqualityAcrossPlanesAndCoordinateEdges() {
        int[] coordinates = {-1, 0, 1, 16383, 16384, Integer.MIN_VALUE, Integer.MAX_VALUE};
        Map<Position, Integer> map = new HashMap<>();
        int id = 0;
        for (int x : coordinates) for (int y : coordinates) for (int z = 0; z < 4; z++) {
            map.put(new Position(x, y, z), id++);
        }
        assertEquals(coordinates.length * coordinates.length * 4, map.size());
        id = 0;
        for (int x : coordinates) for (int y : coordinates) for (int z = 0; z < 4; z++) {
            assertEquals(id++, map.remove(new Position(x, y, z)));
        }
        assertTrue(map.isEmpty());
    }

    @Test
    void chunkKeysPreserveEqualityAcrossSignedAndPackingEdges() {
        int[] coordinates = {-1, 0, 1, 65535, 65536, Integer.MIN_VALUE, Integer.MAX_VALUE};
        Map<Chunk, Integer> map = new ConcurrentHashMap<>();
        int id = 0;
        for (int x : coordinates) for (int y : coordinates) {
            map.put(new Chunk(x, y), id++);
        }
        assertEquals(coordinates.length * coordinates.length, map.size());
        id = 0;
        for (int x : coordinates) for (int y : coordinates) {
            assertEquals(id++, map.remove(new Chunk(x, y)));
        }
        assertTrue(map.isEmpty());
    }

    private void assertDistribution(int[] buckets, int minimumOccupied) {
        int occupied = 0;
        int maximum = 0;
        for (int count : buckets) {
            if (count > 0) occupied++;
            maximum = Math.max(maximum, count);
        }
        assertTrue(occupied >= minimumOccupied, "occupied buckets: " + occupied);
        assertTrue(maximum <= 8, "maximum keys per bucket: " + maximum);
    }

    @Test
    void nearbyPositionsDoNotConcentrateInHashMapBuckets() {
        for (int base : new int[]{0, 2624, 3200}) for (int z = 0; z < 4; z++) {
            int[] buckets = new int[16384];
            for (int x = base; x < base + 64; x++) for (int y = base; y < base + 64; y++) {
                int hash = new Position(x, y, z).hashCode();
                buckets[(hash ^ (hash >>> 16)) & (buckets.length - 1)]++;
            }
            assertDistribution(buckets, 3072);
        }
    }

    @Test
    void nearbyChunksDoNotConcentrateInConcurrentHashMapBuckets() {
        for (int base : new int[]{0, 328, 400}) {
            int[] buckets = new int[65536];
            for (int x = base; x < base + 64; x++) for (int y = base; y < base + 64; y++) {
                int hash = new Chunk(x, y).hashCode();
                buckets[(hash ^ (hash >>> 16)) & (buckets.length - 1)]++;
            }
            assertDistribution(buckets, 3686);
        }
    }

    @Test
    void equalChunkKeysReuseRepositoryWithoutChangingCoordinateIdentifiers() {
        ChunkManager manager = new ChunkManager(mock(World.class, RETURNS_DEEP_STUBS));
        Position tile = new Position(3207, 3207, 3);
        // Luna stores chunk coordinates relative to the six-chunk local-view offset.
        Chunk chunk = new Chunk(394, 394);
        assertEquals(chunk, tile.getChunk());
        assertEquals(new Position(3200, 3200), chunk.getAbsPosition());
        assertEquals(12850, tile.getRegionId());
        assertSame(manager.load(chunk), manager.load(new Chunk(394, 394)));
        assertSame(manager.load(chunk), manager.load(tile));
        assertNotSame(manager.load(chunk), manager.load(new Chunk(395, 394)));
    }
}
