package io.luna.game.cache.map;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Collection of decoded {@link MapTileGrid}s for the full map.
 * <p>
 * Each entry maps a {@link MapIndex} to the decoded {@link MapTileGrid} for that region (64×64×4 tiles).
 *
 * @author lare96
 */
public final class MapTileGridSet implements Iterable<Map.Entry<MapIndex, MapTileGrid>> {

    /**
     * Region terrain grids keyed by {@link MapIndex}.
     */
    private final ImmutableMap<MapIndex, MapTileGrid> gridMap;

    /**
     * Creates a new {@link MapTileGridSet}.
     *
     * @param gridMap The decoded tile grids keyed by index.
     */
    public MapTileGridSet(ImmutableMap<MapIndex, MapTileGrid> gridMap) {
        this.gridMap = gridMap;
    }

    /**
     * Retrieves the decoded {@link MapTileGrid} for {@code index}.
     *
     * @param index The map index to look up.
     * @return The decoded grid for that region.
     * @throws NoSuchElementException If no grid exists for {@code index}.
     */
    public MapTileGrid getGrid(MapIndex index) {
        MapTileGrid grid = gridMap.get(index);
        if (grid == null) {
            throw new NoSuchElementException("No grid found for map index " + index.getRegion());
        }
        return grid;
    }

    /**
     * Iterates all (index -> grid) entries.
     *
     * @return An iterator over the entry set.
     */
    @NotNull
    @Override
    public Iterator<Map.Entry<MapIndex, MapTileGrid>> iterator() {
        return gridMap.entrySet().iterator();
    }
}
