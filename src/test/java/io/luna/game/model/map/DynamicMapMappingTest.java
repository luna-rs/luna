package io.luna.game.model.map;

import io.luna.LunaContext;
import io.luna.game.model.Position;
import io.luna.game.model.Region;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.map.builder.DynamicMapChunk;
import io.luna.game.model.map.builder.DynamicMapChunk.Rotation;
import io.luna.game.model.map.builder.DynamicMapPalette;
import io.luna.net.msg.out.DynamicMapMessageWriter;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Exercises non-contiguous, repeated, rotated and plane-remapped palette coordinates and native packet encoding.
 *
 * @author lare96
 */
class DynamicMapMappingTest {
    private final Chunk source = new Chunk(400, 500);
    private final DynamicMapSpace space = new DynamicMapSpace(new Region(110, 20));

    private DynamicMap map(DynamicMapPalette palette) {
        DynamicMap map = new DynamicMap(mock(LunaContext.class, RETURNS_DEEP_STUBS), source, palette, player -> null);
        map.setAssignedSpace(space);
        return map;
    }

    @Test
    void mapsEveryTileForEveryRotationAndPlane() {
        for (Rotation rotation : Rotation.values()) {
            DynamicMapPalette palette = new DynamicMapPalette().setChunk(3, 9, 2,
                    new DynamicMapChunk(source, 1, rotation));
            DynamicMap map = map(palette);
            HashSet<Position> unique = new HashSet<>();
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 8; y++) {
                    int expectedX = switch (rotation) {
                        case NORMAL -> x;
                        case CW_90_DEGREES -> y;
                        case CW_180_DEGREES -> 7 - x;
                        case CW_270_DEGREES -> 7 - y;
                    };
                    int expectedY = switch (rotation) {
                        case NORMAL -> y;
                        case CW_90_DEGREES -> 7 - x;
                        case CW_180_DEGREES -> 7 - y;
                        case CW_270_DEGREES -> x;
                    };
                    Position result = map.getInstancePosition(source.getAbsPosition().translate(x, y).setZ(1));
                    assertEquals(space.abs().translate(24 + expectedX, 72 + expectedY).setZ(2), result);
                    assertTrue(map.contains(result));
                    unique.add(result);
                }
            }
            assertEquals(64, unique.size());
            assertTrue(map.getInstancePositions(source.getAbsPosition()).isEmpty());
        }
    }

    @Test
    void repeatedSourcesReturnStableFirstMatchAndAllMatches() {
        DynamicMapChunk chunk = new DynamicMapChunk(source, 0);
        DynamicMap map = map(new DynamicMapPalette().setChunk(9, 8, 0, chunk)
                .setChunk(2, 4, 0, chunk).setChunk(1, 1, 3, chunk));
        List<Position> expected = List.of(space.abs().translate(16, 32), space.abs().translate(72, 64),
                space.abs().translate(8, 8).setZ(3));
        assertEquals(expected, map.getInstancePositions(source.getAbsPosition()));
        assertEquals(expected.get(0), map.getInstancePosition(source.getAbsPosition()));
        assertThrows(IllegalArgumentException.class, () -> map.getInstancePosition(1, 1));
        map.setAssignedSpace(null);
        assertThrows(IllegalStateException.class, () -> map.getInstancePosition(source.getAbsPosition()));
    }

    @Test
    void convenienceFillMethodsMutateTheReceivingPaletteAndKeepTheirAnchors() {
        DynamicMapPalette chunks = new DynamicMapPalette();
        assertSame(chunks, chunks.fillWithChunks(source, 2, 6));
        assertEquals(source, chunks.getChunk(6, 6, 0).getChunk());
        assertEquals(source.translate(-6, -6), chunks.getChunk(0, 0, 0).getChunk());
        assertEquals(source.translate(6, 6), chunks.getChunk(12, 12, 0).getChunk());
        DynamicMapPalette region = new DynamicMapPalette();
        Region realRegion = new Region(50, 50);
        assertSame(region, region.fillWithRegion(realRegion, 3));
        assertEquals(realRegion.getAbsPosition().getChunk(), region.getChunk(4, 4, 0).getChunk());
        assertEquals(64, DynamicMapPalette.getAllChunksInRegion(realRegion.getId()).size());
        assertThrows(IllegalArgumentException.class, () -> region.setChunkRadius(6, 6, 0, source, 0, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> region.setRegion(0, 4, realRegion, 0));
        assertThrows(IllegalArgumentException.class, () -> region.setChunk(13, 0, 0, null));
    }

    @Test
    void builtMapAndArraySnapshotsDoNotChangeWithTheirBuilder() {
        DynamicMapPalette palette = new DynamicMapPalette().setChunk(6, 6, 0, new DynamicMapChunk(source, 0));
        DynamicMap map = map(palette);
        palette.setChunk(6, 6, 0, null);
        map.getPalette().setChunk(6, 6, 0, null);
        var snapshot = map.getPalette().getArray();
        snapshot[6][6][0] = null;
        assertEquals(space.abs().translate(48, 48), map.getInstancePosition(source.getAbsPosition()));
    }

    @Test
    void rotatedMultiTileFootprintsMatchNativeClientAnchors() {
        DynamicMapChunk chunk = new DynamicMapChunk(source, 0, Rotation.CW_90_DEGREES);
        assertEquals(3, chunk.rotateX(2, 3, 2, 3));
        assertEquals(4, chunk.rotateY(2, 3, 2, 3));
        chunk = new DynamicMapChunk(source, 0, Rotation.CW_270_DEGREES);
        assertEquals(2, chunk.rotateX(2, 3, 2, 3));
        assertEquals(2, chunk.rotateY(2, 3, 2, 3));
    }

    @Test
    void packetShiftsPaletteForThePlayersCurrentSceneWindow() {
        DynamicMapChunk chunk = new DynamicMapChunk(source, 3, Rotation.CW_270_DEGREES);
        DynamicMap map = map(new DynamicMapPalette().setChunk(6, 6, 2, chunk));
        Position viewer = space.abs().translate(60, 52);
        var buffer = Unpooled.buffer();
        try {
            var message = new DynamicMapMessageWriter(map, viewer).write(null, buffer);
            assertEquals(53, message.getOpcode());
            int x = buffer.readUnsignedByte() << 8 | (buffer.readUnsignedByte() - 128 & 255);
            assertEquals(viewer.getChunkX(), x);
            int bit = 16;
            int found = 0;
            for (int z = 0; z < 4; z++) for (int sx = 0; sx < 13; sx++) for (int sy = 0; sy < 13; sy++) {
                if (bits(buffer, bit++, 1) == 0) continue;
                int packed = bits(buffer, bit, 26);
                bit += 26;
                assertEquals(2, z);
                assertEquals(5, sx);
                assertEquals(6, sy);
                assertEquals(3 << 24 | 406 << 14 | 506 << 3 | 3 << 1, packed);
                found++;
            }
            assertEquals(1, found);
            buffer.readerIndex((bit + 7) / 8);
            int y = buffer.readUnsignedByte() << 8 | (buffer.readUnsignedByte() - 128 & 255);
            assertEquals(viewer.getChunkY(), y);
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private static int bits(io.netty.buffer.ByteBuf buffer, int start, int length) {
        int value = 0;
        for (int bit = start; bit < start + length; bit++)
            value = value << 1 | (buffer.getUnsignedByte(bit / 8) >> (7 - bit % 8) & 1);
        return value;
    }
}
