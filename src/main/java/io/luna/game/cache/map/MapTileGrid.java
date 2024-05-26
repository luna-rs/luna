package io.luna.game.cache.map;

public final class MapTileGrid {

    private final MapTile[][][] grid;

    public MapTileGrid(MapTile[][][] newGrid) {
        grid = newGrid.clone();
    }

    public MapTile getMapTile(int x, int y, int z) {
        return grid[z][x][y];
    }
}
