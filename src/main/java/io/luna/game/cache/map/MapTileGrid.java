package io.luna.game.cache.map;

import io.luna.game.model.RegionPosition;

/**
 * A grid of {@link MapTile} types that make up a {@link RegionPosition}. The grid is 64x64x4 in total size and helps
 * organize the data for individual tiles into a set grouped by region.
 */
public final class MapTileGrid {

    /**
     * The region this grid of {@link MapTile} types is describing.
     */
    private final RegionPosition region;

    /**
     * The grid array.
     */
    private final MapTile[][][] grid;

    /**
     * Creates a new {@link MapTileGrid}.
     *
     * @param region The region this grid of {@link MapTile} types is describing.
     * @param newGrid The grid array.
     */
    public MapTileGrid(RegionPosition region, MapTile[][][] newGrid) {
        this.region = region;
        grid = newGrid.clone();
    }

    /**
     * @return The region this grid of {@link MapTile} types is describing.
     */
    public RegionPosition getRegion() {
        return region;
    }

    /**
     * Retrieves a {@link MapTile} on the grid, within the {@link #region}.
     *
     * @param x The {@code x} offset from the region.
     * @param y The {@code y} offset from the region.
     * @param plane The plane.
     * @return The tile, {@code null} if no data for the tile, or throws {@link ArrayIndexOutOfBoundsException}.
     */
    public MapTile getTile(int x, int y, int plane) {
        return grid[plane][x][y];
    }
}
