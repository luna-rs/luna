package io.luna.game.cache.map;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.RegionPosition;

/**
 * Represents the set of all {@link MapIndex} types along with the decoded object and tile data.
 *
 * @author lare96
 */
public final class MapIndexTable {

    /**
     * The index table, mapped by region.
     */
    private final ImmutableMap<RegionPosition, MapIndex> indexTable;

    /**
     * The object set.
     */
    private final MapObjectSet objectSet;

    /**
     * The tile set.
     */
    private final MapTileGridSet tileSet;

    /**
     * Creates a new {@link MapIndexTable}.
     *
     * @param indexTable The index table, mapped by region.
     * @param objectSet The object set.
     * @param tileSet The tile set.
     */
    public MapIndexTable(ImmutableMap<RegionPosition, MapIndex> indexTable, MapObjectSet objectSet, MapTileGridSet tileSet) {
        this.indexTable = indexTable;
        this.objectSet = objectSet;
        this.tileSet = tileSet;
    }

    /**
     * @return All mapped {@link MapIndex} types.
     */
    public ImmutableCollection<MapIndex> getAllIndices() {
        return indexTable.values();
    }

    /**
     * @return The keys ({@link RegionPosition}) of all {@link MapIndex} types.
     */
    public ImmutableSet<RegionPosition> getAllRegions() {
        return indexTable.keySet();
    }

    /**
     * @return The index table, mapped by region.
     */
    public ImmutableMap<RegionPosition, MapIndex> getIndexTable() {
        return indexTable;
    }

    /**
     * @return The object set.
     */
    public MapObjectSet getObjectSet() {
        return objectSet;
    }

    /**
     * @return The tile set.
     */
    public MapTileGridSet getTileSet() {
        return tileSet;
    }
}
