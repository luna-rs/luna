package io.luna.game.cache.map;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.Position;
import io.luna.game.model.Region;

import java.util.NoSuchElementException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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
     * @throws NullPointerException If any argument is {@code null}.
     */
    public MapIndexTable(ImmutableMap<Region, MapIndex> indexTable, MapObjectSet objectSet, MapTileGridSet tileSet) {
        this.indexTable = checkNotNull(indexTable, "Map index table cannot be null.");
        this.objectSet = checkNotNull(objectSet, "Map object set cannot be null.");
        this.tileSet = checkNotNull(tileSet, "Map tile set cannot be null.");
    }

    /**
     * Retrieves the decoded terrain tile at the supplied absolute position.
     * <p>
     * Coordinates must fit the cache's 256-by-256 region map: X and Y range from {@code 0} to {@code 16383},
     * and the plane must be within {@link Position#HEIGHT_LEVELS}. Missing map data is reported explicitly;
     * this method does not substitute an empty tile.
     *
     * @param position The absolute tile position.
     * @return The decoded tile at {@code position}.
     * @throws NullPointerException If {@code position} is {@code null}.
     * @throws IllegalArgumentException If the coordinates or plane are outside the supported bounds.
     * @throws NoSuchElementException If the region, terrain grid, or tile has not been decoded.
     */
    public MapTile getTile(Position position) {
        checkNotNull(position, "Tile position cannot be null.");
        checkArgument(position.getX() >= 0 && position.getX() < 16384 &&
                        position.getY() >= 0 && position.getY() < 16384,
                "Tile coordinates must be between 0 and 16383: %s", position);
        checkArgument(Position.HEIGHT_LEVELS.contains(position.getZ()),
                "Tile plane must be between 0 and 3: %s", position);
        MapIndex index = indexTable.get(position.getRegion());
        if (index == null) {
            throw new NoSuchElementException("No map index found for tile " + position);
        }
        MapTile tile = tileSet.getGrid(index).getTile(position);
        if (tile == null) {
            throw new NoSuchElementException("No decoded tile found at " + position);
        }
        return tile;
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
