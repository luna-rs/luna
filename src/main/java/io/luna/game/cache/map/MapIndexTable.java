package io.luna.game.cache.map;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.Region;

/**
 * Holds the decoded map index table and its derived decoded data sets.
 * <p>
 * This is effectively the "map database" produced by decoding the cache:
 * <ul>
 *   <li>An index table mapping each {@link Region} to its {@link MapIndex} (tile/object archive ids)</li>
 *   <li>The decoded static {@link MapObject} placements for all regions</li>
 *   <li>The decoded {@link MapTileGrid} terrain for all regions</li>
 * </ul>
 *
 * @author lare96
 */
public final class MapIndexTable {

    /**
     * The index table keyed by region.
     */
    private final ImmutableMap<Region, MapIndex> indexTable;

    /**
     * All decoded static map objects across the map.
     */
    private final MapObjectSet objectSet;

    /**
     * All decoded tile grids across the map.
     */
    private final MapTileGridSet tileSet;

    /**
     * Creates a new {@link MapIndexTable}.
     *
     * @param indexTable Index entries keyed by region.
     * @param objectSet Decoded object placements.
     * @param tileSet Decoded terrain grids.
     */
    public MapIndexTable(ImmutableMap<Region, MapIndex> indexTable, MapObjectSet objectSet, MapTileGridSet tileSet) {
        this.indexTable = indexTable;
        this.objectSet = objectSet;
        this.tileSet = tileSet;
    }

    /**
     * Returns all {@link MapIndex} entries (one per region).
     *
     * @return All indices.
     */
    public ImmutableCollection<MapIndex> getAllIndices() {
        return indexTable.values();
    }

    /**
     * Returns all regions that exist in the index table.
     *
     * @return All regions.
     */
    public ImmutableSet<Region> getAllRegions() {
        return indexTable.keySet();
    }

    /**
     * Returns the index table keyed by region.
     *
     * @return The region -> index map.
     */
    public ImmutableMap<Region, MapIndex> getIndexTable() {
        return indexTable;
    }

    /**
     * @return All decoded object placements.
     */
    public MapObjectSet getObjectSet() {
        return objectSet;
    }

    /**
     * @return All decoded terrain grids.
     */
    public MapTileGridSet getTileSet() {
        return tileSet;
    }
}
