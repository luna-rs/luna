package io.luna.game.cache.map;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

public class MapTileGridSet implements Iterable<Map.Entry<MapIndex, MapTileGrid>> {


    private final ImmutableMap<MapIndex, MapTileGrid> tiles;

    public MapTileGridSet(ImmutableMap<MapIndex, MapTileGrid> tiles) {
        this.tiles = tiles;
    }

    public MapTileGrid getGrid(MapIndex index) {
        return tiles.get(index);
    }

    public MapTile getMapTile(MapIndex index, int x, int y, int z) {
        return getGrid(index).getMapTile(x, y, z);
    }

    @NotNull
    @Override
    public Iterator<Map.Entry<MapIndex, MapTileGrid>> iterator() {
        return tiles.entrySet().iterator();
    }
}
