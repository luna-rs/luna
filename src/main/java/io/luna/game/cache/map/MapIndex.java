package io.luna.game.cache.map;

import com.google.common.base.Objects;
import io.luna.game.model.Region;

/**
 * Maps a single {@link Region} to its corresponding cache file identifiers.
 * <p>
 * In the RuneScape cache, each region is backed by two archives:
 * <ul>
 *   <li><b>Tile/terrain</b> data (heights, overlays, underlays, tile flags)</li>
 *   <li><b>Object</b> data (static world objects placed in the region)</li>
 * </ul>
 * <p>
 * {@link MapIndex} is the decoded entry tying the region id to those archive ids.
 *
 * @author lare96
 */
public final class MapIndex {

    /**
     * The region this index entry describes.
     */
    private final Region region;

    /**
     * Cache file/archive id for the region's terrain (tile) data.
     */
    private final int tileFileId;

    /**
     * Cache file/archive id for the region's object placement data.
     */
    private final int objectFileId;

    /**
     * Whether this region is marked as "priority" in the index.
     *
     * <p>This flag is typically used to prefer decoding/loading certain regions first.
     */
    private final boolean priority;

    /**
     * Creates a new {@link MapIndex}.
     *
     * @param region The region this index entry describes.
     * @param tileFileId Cache id for the region's terrain/tile data.
     * @param objectFileId Cache id for the region's object placement data.
     * @param priority Whether this region is marked as priority.
     */
    public MapIndex(Region region, int tileFileId, int objectFileId, boolean priority) {
        this.region = region;
        this.tileFileId = tileFileId;
        this.objectFileId = objectFileId;
        this.priority = priority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MapIndex mapIndex = (MapIndex) o;
        return Objects.equal(region, mapIndex.region);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(region);
    }

    /**
     * @return The region this index entry describes.
     */
    public Region getRegion() {
        return region;
    }

    /**
     * @return Cache file/archive id for the region's terrain (tile) data.
     */
    public int getTileFileId() {
        return tileFileId;
    }

    /**
     * @return Cache file/archive id for the region's object placement data.
     */
    public int getObjectFileId() {
        return objectFileId;
    }

    /**
     * @return {@code true} if this region is marked as priority.
     */
    public boolean isPriority() {
        return priority;
    }
}
