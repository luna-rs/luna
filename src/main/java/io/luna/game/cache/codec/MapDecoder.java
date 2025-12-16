package io.luna.game.cache.codec;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import io.luna.LunaContext;
import io.luna.game.cache.Archive;
import io.luna.game.cache.Cache;
import io.luna.game.cache.CacheDecoder;
import io.luna.game.cache.CacheUtils;
import io.luna.game.cache.map.MapIndex;
import io.luna.game.cache.map.MapIndexTable;
import io.luna.game.cache.map.MapObject;
import io.luna.game.cache.map.MapObjectSet;
import io.luna.game.cache.map.MapTile;
import io.luna.game.cache.map.MapTileGrid;
import io.luna.game.cache.map.MapTileGridSet;
import io.luna.game.model.Position;
import io.luna.game.model.Region;
import io.luna.game.model.object.ObjectDirection;
import io.luna.game.model.object.ObjectType;
import io.netty.buffer.ByteBuf;

import java.util.function.Consumer;

import static io.luna.game.cache.CacheUtils.MAP_PLANES;
import static io.luna.game.cache.CacheUtils.MAP_SIZE;

/**
 * A {@link CacheDecoder} responsible for loading all region-based map data:
 * <ul>
 *     <li>{@link MapIndex} entries (per-region file references).</li>
 *     <li>{@link MapTile} height/overlay/underlay tile information.</li>
 *     <li>{@link MapObject} world objects (walls, scenery, interactables).</li>
 * </ul>
 *
 * <p>
 * This decoder reads the #377 cache using the original Jagex map format: tiles are stored in <strong>4 planes × 64 × 64</strong>
 * grids, and objects are stored using packed "smart" deltas.
 * </p>
 *
 * <p>
 * After decoding, a thread-safe {@link MapIndexTable} is injected into the global {@link Cache} for access by the world loader.
 * </p>
 *
 * @author lare96
 */
public final class MapDecoder extends CacheDecoder<MapIndex> {

    /**
     * Decoder for a single tile inside a 64×64×4 region.
     * <p>
     * Implements the #377 client’s terrain decoding:
     * <ul>
     *     <li>Height calculation</li>
     *     <li>Overlay/underlay</li>
     *     <li>Tile attributes</li>
     *     <li>Orientation and shape types</li>
     * </ul>
     * <p>
     * Each opcode modifies one part of the tile’s state until an opcode of {@code 0}
     * finalizes the data.
     */
    private static final class MapTileDecoder implements Consumer<Integer> {

        /**
         * Precomputed cosine table (identical to 377 client).
         */
        private static final int[] COSINE = new int[2048];

        static {
            for (int k = 0; k < 2048; k++) {
                COSINE[k] = (int) (65536D * Math.cos(k * 0.0030679614999999999D));
            }
        }

        /**
         * Procedural terrain sampling used in #377 for base heights on plane 0.
         */
        private static int computeHeight(int x, int y) {
            int vertexHeight =
                    (sampleBilinearNoise(x + 45365, y + 0x16713, 4) - 128)
                            + ((sampleBilinearNoise(x + 10294, y + 37821, 2) - 128) >> 1)
                            + ((sampleBilinearNoise(x, y, 1) - 128) >> 2);

            vertexHeight = (int) (vertexHeight * 0.3) + 35;
            if (vertexHeight < 10) return 10;
            if (vertexHeight > 60) return 60;
            return vertexHeight;
        }

        /**
         * Performs bilinear interpolation of noise samples, exactly as the 377 client.
         */
        private static int sampleBilinearNoise(int deltaX, int deltaY, int scale) {
            int x = deltaX / scale;
            int dx = deltaX & (scale - 1);
            int y = deltaY / scale;
            int dy = deltaY & (scale - 1);

            int sw = randomNoiseWeighedSum(x, y);
            int se = randomNoiseWeighedSum(x + 1, y);
            int ne = randomNoiseWeighedSum(x, y + 1);
            int nw = randomNoiseWeighedSum(x + 1, y + 1);

            int a = interpolate(sw, se, dx, scale);
            int b = interpolate(ne, nw, dx, scale);
            return interpolate(a, b, dy, scale);
        }

        /**
         * Computes weighted noise around a vertex (8-neighbour sampling).
         */
        private static int randomNoiseWeighedSum(int x, int y) {
            int dist2 =
                    calculateNoise(x - 1, y - 1) + calculateNoise(x + 1, y - 1)
                            + calculateNoise(x - 1, y + 1) + calculateNoise(x + 1, y + 1);

            int dist1 =
                    calculateNoise(x - 1, y) + calculateNoise(x + 1, y)
                            + calculateNoise(x, y - 1) + calculateNoise(x, y + 1);

            int local = calculateNoise(x, y);

            return dist2 / 16 + dist1 / 8 + local / 4;
        }

        /**
         * Core random noise function used by Jagex landscapes.
         */
        private static int calculateNoise(int x, int seed) {
            int n = x + seed * 57;
            n = n << 13 ^ n;
            int noise = n * (n * n * 15731 + 0xc0ae5) + 0x5208dd0d & 0x7fffffff;
            return (noise >> 19) & 0xff;
        }

        /**
         * Interpolates between two noise values using cosine smoothing.
         */
        private static int interpolate(int a, int b, int delta, int scale) {
            int f = (0x10000 - COSINE[(delta * 1024) / scale]) >> 1;
            return (a * (0x10000 - f) >> 16) + (b * f >> 16);
        }

        // --- Decoder State ---

        private final int x, y, z;
        private final ByteBuf data;
        private final MapTile[][][] tiles;

        private int height;
        private int overlay, overlayType, overlayOrientation;
        private int attributes;
        private int underlay;

        /**
         * Creates a decoder for a single tile.
         *
         * @param x Tile X coordinate within the region (0–63).
         * @param y Tile Y coordinate within the region (0–63).
         * @param z Current plane (0–3).
         * @param data Map tile buffer.
         * @param tiles A partially-filled 4×64×64 grid of tiles.
         */
        private MapTileDecoder(int x, int y, int z, ByteBuf data, MapTile[][][] tiles) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.data = data;
            this.tiles = tiles;
        }

        @Override
        public void accept(Integer opcode) {
            if (opcode == 0) {
                // Compute height from noise or inherit from below.
                if (z == 0) {
                    height = computeHeight(x + 932731, y + 556238) * 8;
                } else {
                    height = tiles[z - 1][x][y].getHeight() + 240;
                }
            } else if (opcode == 1) {
                int h = data.readByte();
                int below = (z == 0) ? 0 : tiles[z - 1][x][y].getHeight();
                height = ((h == 1 ? 0 : h) * 8) + below;
            } else if (opcode < 50) {
                overlay = data.readByte();
                overlayType = (opcode - 2) / 4;
                overlayOrientation = (opcode - 2) % 4;
            } else if (opcode < 82) {
                attributes = opcode - 49;
            } else {
                underlay = opcode - 81;
            }
        }

        /**
         * Finalizes the decoder state into a single {@link MapTile} instance.
         */
        public MapTile toMapTile() {
            return new MapTile(x, y, z, height, overlay, overlayType, overlayOrientation, attributes, underlay);
        }
    }

    // -------------------------------------------------------------------------
    // 1. Decode MapIndex (map_index file)
    // -------------------------------------------------------------------------

    /**
     * Reads all {@link MapIndex} entries from the {@code map_index} file.
     * <p>
     * Each entry contains:
     * <ul>
     *     <li>Region ID</li>
     *     <li>Tile file ID</li>
     *     <li>Object file ID</li>
     *     <li>Priority flag</li>
     * </ul>
     */
    @Override
    public void decode(Cache cache, Builder<MapIndex> decodedObjects) throws Exception {
        Archive versionListArchive = Archive.decode(cache.getFile(0, 5));
        ByteBuf buf = versionListArchive.getFileData("map_index");

        try {
            int count = buf.readableBytes() / 7;
            for (int i = 0; i < count; i++) {
                Region region = new Region(buf.readUnsignedShort());
                int tileFileId = buf.readUnsignedShort();
                int objectFileId = buf.readUnsignedShort();
                boolean priority = buf.readBoolean();
                decodedObjects.add(new MapIndex(region, tileFileId, objectFileId, priority));
            }
        } finally {
            buf.release();
        }
    }

    // -------------------------------------------------------------------------
    // 2. Register tiles, objects, and collision into the world
    // -------------------------------------------------------------------------

    /**
     * Converts decoded {@link MapIndex} records into concrete world data:
     * <ul>
     *     <li>Decodes all tiles and their heights/overlays</li>
     *     <li>Decodes all world objects</li>
     *     <li>Builds a global {@link MapIndexTable}</li>
     * </ul>
     * Then queues the next step to run on the game thread, which:
     * <ul>
     *     <li>Applies collision data from map tiles</li>
     *     <li>Registers all map objects</li>
     * </ul>
     */
    @Override
    public void handle(LunaContext ctx,
                       Cache cache,
                       ImmutableList<MapIndex> decodedObjects) throws Exception {

        // Build region -> index mapping.
        ImmutableMap.Builder<Region, MapIndex> tableBuilder = ImmutableMap.builder();
        decodedObjects.forEach(idx -> tableBuilder.put(idx.getRegion(), idx));
        ImmutableMap<Region, MapIndex> indexMap = tableBuilder.build();

        ImmutableList<MapObject> mapObjects = decodeMapObjects(cache, indexMap.values());
        ImmutableMap<MapIndex, MapTileGrid> mapTiles = decodeMapTiles(cache, indexMap.values());

        MapIndexTable table = new MapIndexTable(indexMap, new MapObjectSet(mapObjects), new MapTileGridSet(mapTiles));
        cache.setMapIndexTable(table);
    }

    // -------------------------------------------------------------------------
    // 3. Decode map objects (4, objectFileId)
    // -------------------------------------------------------------------------

    /**
     * Decodes all object spawns inside every region using the original 377 delta-smart format.
     * <p>
     * Each object is encoded as:
     * <ul>
     *     <li>Delta ID stream</li>
     *     <li>Delta position stream (packed X/Y/plane)</li>
     *     <li>Type and rotation</li>
     * </ul>
     *
     * @return Immutable list of all region objects.
     */
    private ImmutableList<MapObject> decodeMapObjects(Cache cache,
                                                      ImmutableCollection<MapIndex> indices)
            throws Exception {

        ImmutableList.Builder<MapObject> list = ImmutableList.builder();

        for (MapIndex index : indices) {
            int fileId = index.getObjectFileId();
            Position base = index.getRegion().getAbsPosition();

            ByteBuf data = CacheUtils.unzip(cache.getFile(4, fileId));
            try {
                int id = -1;
                while (true) {
                    int idOffset = CacheUtils.readSmart(data);
                    if (idOffset == 0) break;
                    id += idOffset;

                    int packed = 0;
                    while (true) {
                        int posOffset = CacheUtils.readSmart(data);
                        if (posOffset == 0) break;
                        packed += posOffset - 1;

                        int x = (packed >> 6) & 0x3F;
                        int y = packed & 0x3F;
                        int plane = (packed >> 12) & 0x3;

                        int info = data.readUnsignedByte();
                        ObjectType type = ObjectType.ALL.get(info >> 2);
                        ObjectDirection rotation = ObjectDirection.ALL.get(info & 3);

                        // Edge cases with incorrect planes, unsure why this is.
                        if ((id == 2294 || id == 2295 || id == 2311 || id == 2297) && plane == 1)
                            plane = 0;

                        Position pos = base.translate(x, y).setZ(plane);
                        list.add(new MapObject(id, pos, type, rotation));
                    }
                }
            } finally {
                data.release();
            }
        }

        return list.build();
    }

    // -------------------------------------------------------------------------
    // 4. Decode map tiles (heightmap, overlays, flags)
    // -------------------------------------------------------------------------

    /**
     * Decodes all {@link MapTile} entries inside each region.
     * <p>
     * Tile format is the classic 377 opcode-driven terrain format:
     * <ul>
     *     <li>Height (noise or explicit)</li>
     *     <li>Overlay/shape/orientation</li>
     *     <li>Underlay</li>
     *     <li>Attribute flags</li>
     * </ul>
     */
    private ImmutableMap<MapIndex, MapTileGrid> decodeMapTiles(
            Cache cache,
            ImmutableCollection<MapIndex> indices) throws Exception {

        ImmutableMap.Builder<MapIndex, MapTileGrid> map = ImmutableMap.builder();

        for (MapIndex index : indices) {
            MapTile[][][] tiles = new MapTile[MAP_PLANES][MAP_SIZE][MAP_SIZE];

            ByteBuf data = CacheUtils.unzip(cache.getFile(4, index.getTileFileId()));
            try {
                for (int z = 0; z < MAP_PLANES; z++) {
                    for (int x = 0; x < MAP_SIZE; x++) {
                        for (int y = 0; y < MAP_SIZE; y++) {

                            MapTileDecoder decoder = new MapTileDecoder(x, y, z, data, tiles);

                            int opcode = data.readUnsignedByte();
                            decoder.accept(opcode);
                            while (opcode >= 2) {
                                opcode = data.readUnsignedByte();
                                decoder.accept(opcode);
                            }

                            tiles[z][x][y] = decoder.toMapTile();
                        }
                    }
                }
                map.put(index, new MapTileGrid(index.getRegion(), tiles));
            } finally {
                data.release();
            }
        }

        return map.build();
    }
}
