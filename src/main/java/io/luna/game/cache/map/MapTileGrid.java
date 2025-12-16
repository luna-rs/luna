package io.luna.game.cache.map;

import io.luna.game.model.Position;
import io.luna.game.model.Region;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Represents the complete 64×64×4 tile grid for a single {@link Region}.
 *
 * @author lare96
 */
public final class MapTileGrid {

    /**
     * The region whose tiles are represented by this grid.
     */
    private final Region region;

    /**
     * The 3D tile array (plane -> x -> y).
     * <p>
     * Each entry is a fully decoded {@link MapTile} describing overlay, height, underlay, and attributes.
     */
    private final MapTile[][][] grid;

    /**
     * Creates a new {@link MapTileGrid} for the given region.
     *
     * @param region The region these tiles belong to.
     * @param newGrid The decoded 3D tile array ({@code [plane][x][y]}).
     */
    public MapTileGrid(Region region, MapTile[][][] newGrid) {
        this.region = region;
        grid = Arrays.copyOf(newGrid, newGrid.length);
    }

    /**
     * Iterates over every {@link MapTile} in the grid (all 64×64×4 tiles), invoking the supplied action
     * once per tile.
     *
     * <p>
     * Order of iteration:
     * <pre>
     * for each plane
     *     for x from 0 to 63
     *         for y from 0 to 63
     * </pre>
     * </p>
     *
     * @param action A callback that receives each {@link MapTile}.
     */
    public void forEach(Consumer<MapTile> action) {
        for (MapTile[][] planeTiles : grid) {
            for (MapTile[] planeTile : planeTiles) {
                for (MapTile tile : planeTile) {
                    action.accept(tile);
                }
            }
        }
    }

    /**
     * Retrieves a {@link MapTile} from the grid using local region coordinates.
     *
     * @param x The local X offset inside the region (0–63).
     * @param y The local Y offset inside the region (0–63).
     * @param plane The plane (0–3).
     * @return The corresponding tile (never {@code null} for real cache regions).
     * @throws ArrayIndexOutOfBoundsException If any coordinate lies outside valid bounds.
     */
    public MapTile getTile(int x, int y, int plane) {
        return grid[plane][x][y];
    }

    /**
     * Retrieves a {@link MapTile} from the grid using absolute coordinates.
     *
     * @param abs The absolute coordinates.
     * @return The corresponding tile, or {@code null} if the coordinates are not within this grid.
     */
    public MapTile getTile(Position abs) {
        return getTile(abs.getX() % 64, abs.getY() % 64, abs.getZ());
    }

    /**
     * Returns the {@link Region} this tile grid describes.
     */
    public Region getRegion() {
        return region;
    }
}
