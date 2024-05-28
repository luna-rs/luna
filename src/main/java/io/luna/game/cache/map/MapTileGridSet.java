package io.luna.game.cache.map;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * A collection of all the {@link MapTileGrid} types that make up the entire rs2 map, linked to their respective
 * {@link MapIndex} types.
 *
 * @author lare96
 */
public class MapTileGridSet implements Iterable<Map.Entry<MapIndex, MapTileGrid>> {

    /**
     * The map of grids.
     */
    private final ImmutableMap<MapIndex, MapTileGrid> gridMap;

    /**
     * Creates a new {@link MapTileGridSet}.
     *
     * @param gridMap The map of grids.
     */
    public MapTileGridSet(ImmutableMap<MapIndex, MapTileGrid> gridMap) {
        this.gridMap = gridMap;
    }

    /**
     * Retrieves a {@link MapTileGrid} from the map based on the {@link MapIndex}.
     *
     * @param index The map index to retrieve the grid for.
     * @return The grid, or throws {@link NoSuchElementException}.
     */
    public MapTileGrid getGrid(MapIndex index) {
        MapTileGrid grid = gridMap.get(index);
        if (grid == null) {
            throw new NoSuchElementException("No grid found for map index " + index.getRegion());
        }
        return grid;
    }

    @NotNull
    @Override
    public Iterator<Map.Entry<MapIndex, MapTileGrid>> iterator() {
        return gridMap.entrySet().iterator();
    }
}
