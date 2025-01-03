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
import io.luna.game.model.World;
import io.luna.game.model.object.ObjectDirection;
import io.luna.game.model.object.ObjectType;
import io.netty.buffer.ByteBuf;

import java.util.function.Consumer;

import static io.luna.game.cache.CacheUtils.MAP_PLANES;
import static io.luna.game.cache.CacheUtils.MAP_SIZE;

/**
 * A {@link CacheDecoder} implementation that loads map indexes, objects, and tiles from the cache.
 *
 * @author lare96
 */
public final class MapDecoder extends CacheDecoder<MapIndex> {

    /**
     * A {@link Consumer} function that decodes map tiles based on the given opcode.
     */
    private static final class MapTileDecoder implements Consumer<Integer> {

        // Various functions from the #377 client used to decode data.
        private static int computeHeight(int x, int y) {
            int vertexHeight = (method176(x + 45365, y + 0x16713, 4) - 128)
                    + (method176(x + 10294, y + 37821, 2) - 128 >> 1) + (method176(x, y, 1) - 128 >> 2);
            vertexHeight = (int) (vertexHeight * 0.29999999999999999D) + 35;
            if (vertexHeight < 10) {
                vertexHeight = 10;
            } else if (vertexHeight > 60) {
                vertexHeight = 60;
            }
            return vertexHeight;
        }

        private static int method176(final int deltaX, final int deltaY, final int deltaScale) {
            final int x = deltaX / deltaScale;
            final int deltaPrimary = deltaX & deltaScale - 1;
            final int y = deltaY / deltaScale;
            final int deltaSecondary = deltaY & deltaScale - 1;
            final int noiseSW = randomNoiseWeighedSum(x, y);
            final int noiseSE = randomNoiseWeighedSum(x + 1, y);
            final int noiseNE = randomNoiseWeighedSum(x, y + 1);
            final int noiseNW = randomNoiseWeighedSum(x + 1, y + 1);
            final int interpolationA = interpolate(noiseSW, noiseSE, deltaPrimary, deltaScale);
            final int interpolationB = interpolate(noiseNE, noiseNW, deltaPrimary, deltaScale);
            return interpolate(interpolationA, interpolationB, deltaSecondary, deltaScale);
        }

        private static int randomNoiseWeighedSum(final int x, final int y) {
            final int vDist2 = calculateNoise(x - 1, y - 1) + calculateNoise(x + 1, y - 1) + calculateNoise(x - 1, y + 1)
                    + calculateNoise(x + 1, y + 1);
            final int vDist1 = calculateNoise(x - 1, y) + calculateNoise(x + 1, y) + calculateNoise(x, y - 1)
                    + calculateNoise(x, y + 1);
            final int vLocal = calculateNoise(x, y);
            return vDist2 / 16 + vDist1 / 8 + vLocal / 4;
        }

        private static int calculateNoise(final int x, final int seed) {
            int n = x + seed * 57;
            n = n << 13 ^ n;
            final int noise = n * (n * n * 15731 + 0xc0ae5) + 0x5208dd0d & 0x7fffffff;
            return noise >> 19 & 0xff;
        }

        private static int interpolate(final int a, final int b, final int delta, final int deltaScale) {
            final int f = 0x10000 - COSINE[(delta * 1024) / deltaScale] >> 1;
            return (a * (0x10000 - f) >> 16) + (b * f >> 16);
        }

        private static final int[] COSINE = new int[2048];

        static {
            for (int k = 0; k < 2048; k++) {
                COSINE[k] = (int) (65536D * Math.cos(k * 0.0030679614999999999D));
            }
        }

        /**
         * The {@code x} coordinate of the tile being decoded.
         */
        private final int x;

        /**
         * The {@code y} coordinate of the tile being decoded.
         */
        private final int y;

        /**
         * The {@code z} coordinate of the tile being decoded.
         */
        private final int z;

        /**
         * The data to decode.
         */
        private final ByteBuf data;

        /**
         * The current tile map.
         */
        private final MapTile[][][] tiles;

        /**
         * The height of the tile being decoded.
         */
        private int height;

        /**
         * The overlay of the tile being decoded.
         */
        private int overlay;

        /**
         * The overlay type of the tile being decoded.
         */
        private int overlayType;

        /**
         * The overlay orientation of the tile being decoded.
         */
        private int overlayOrientation;

        /**
         * The attributes of the tile being decoded.
         */
        private int attributes;

        /**
         * The underlay of the tile being decoded.
         */
        private int underlay;


        /**
         * Creates a new {@link MapTileDecoder}.
         *
         * @param x The {@code x} coordinate of the tile being decoded.
         * @param y The {@code y} coordinate of the tile being decoded.
         * @param z The {@code z} coordinate of the tile being decoded.
         * @param data The data to decode.
         * @param tiles The current tile map.
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
                if (z == 0) {
                    int offsetX = x - 48;
                    int offsetY = y - 48;
                    height = computeHeight(932731 + x - offsetX, 556238 + y - offsetY) * 8;
                } else {
                    height = tiles[z - 1][x][y].getHeight() + 240;
                }
            } else if (opcode == 1) {
                int newHeight = data.readByte();
                int below = (z == 0) ? 0 : tiles[z - 1][x][y].getHeight();
                height = (((newHeight == 1) ? 0 : newHeight) * 8) + below;
            } else if (opcode < 50) {
                overlay = data.readByte();
                overlayType = (opcode - 2) / 4;
                overlayOrientation = opcode - 2 % 4;
            } else if (opcode < 82) {
                attributes = opcode - 49;
            } else {
                underlay = opcode - 81;
            }
        }

        /**
         * Converts the decoded data into a {@link MapTile} type.
         */
        public MapTile toMapTile() {
            return new MapTile(x, y, z, height, overlay, overlayType, overlayOrientation, attributes, underlay);
        }
    }

    @Override
    public void decode(Cache cache, Builder<MapIndex> decodedObjects) throws Exception {
        Archive versionListArchive = Archive.decode(cache.getFile(0, 5));
        ByteBuf buf = versionListArchive.getFileData("map_index");
        try {
            int indices = buf.readableBytes() / 7;
            for (int i = 0; i < indices; i++) {
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

    @Override
    public void handle(LunaContext ctx, Cache cache, ImmutableList<MapIndex> decodedObjects) throws Exception {
        ImmutableMap.Builder<Region, MapIndex> tableBuilder = ImmutableMap.builder();
        decodedObjects.forEach(it -> tableBuilder.put(it.getRegion(), it));
        ImmutableMap<Region, MapIndex> table = tableBuilder.build();

        ImmutableList<MapObject> mapObjects = decodeMapObjects(cache, table.values());
        ImmutableMap<MapIndex, MapTileGrid> mapTiles = decodeMapTiles(cache, table.values());

        MapIndexTable indexTable = new MapIndexTable(table,
                new MapObjectSet(mapObjects),
                new MapTileGridSet(mapTiles));
        cache.setMapIndexTable(indexTable);

        ctx.getGame().sync(() -> {
            World world = ctx.getWorld();
            for (MapObject object : indexTable.getObjectSet().getObjects()) {
                world.getObjects().register(object.toGameObject(ctx));
            }
        });
    }

    /**
     * Decodes the {@link MapObject} types using the provided cache and map indices.
     *
     * @param cache The cache resource.
     * @param indices The map indices.
     * @return The collection of map objects.
     * @throws Exception If any errors occur.
     */
    private ImmutableList<MapObject> decodeMapObjects(Cache cache, ImmutableCollection<MapIndex> indices) throws Exception {
        ImmutableList.Builder<MapObject> mapObjects = ImmutableList.builder();
        for (MapIndex index : indices) {
            int objectFileId = index.getObjectFileId();
            Position basePosition = index.getRegion().getAbsPosition();
            ByteBuf data = CacheUtils.unzip(cache.getFile(4, objectFileId));
            try {
                int id = -1;
                int idOffset;
                while (true) {
                    idOffset = CacheUtils.readSmart(data);
                    if (idOffset == 0) {
                        break;
                    }
                    id += idOffset;

                    int objectPositionData = 0;
                    int objectDataOffset;
                    while (true) {
                        objectDataOffset = CacheUtils.readSmart(data);
                        if (objectDataOffset == 0) {
                            break;
                        }
                        objectPositionData += objectDataOffset - 1;

                        int offsetX = objectPositionData >> 6 & 0x3f;
                        int offsetY = objectPositionData & 0x3f;
                        int plane = objectPositionData >> 12 & 0x3;
                        int otherData = data.readUnsignedByte();

                        ObjectType type = ObjectType.ALL.get(otherData >> 2);
                        ObjectDirection rotation = ObjectDirection.ALL.get(otherData & 3);
                        Position position = basePosition.translate(offsetX, offsetY).setZ(plane);
                        mapObjects.add(new MapObject(id, position, type, rotation));
                    }
                }
            } finally {
                data.release();
            }
        }
        return mapObjects.build();
    }

    /**
     * Decodes the {@link MapTile} types using the provided cache and map indices.
     *
     * @param cache The cache resource.
     * @param indices The map indices.
     * @return The tile map.
     * @throws Exception If any errors occur.
     */
    private ImmutableMap<MapIndex, MapTileGrid> decodeMapTiles(Cache cache, ImmutableCollection<MapIndex> indices) throws Exception {
        ImmutableMap.Builder<MapIndex, MapTileGrid> tileMap = ImmutableMap.builder();
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
                tileMap.put(index, new MapTileGrid(index.getRegion(), tiles));
            } finally {
                data.release();
            }
        }
        return tileMap.build();
    }
}