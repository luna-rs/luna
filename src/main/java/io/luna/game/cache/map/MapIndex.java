package io.luna.game.cache.map;

import com.google.common.base.Objects;
import io.luna.game.model.Region;

/**
 * A class representing a single map index. Their purpose is to map a single {@link Region} to a tile and
 * object file in the cache.
 *
 * @author lare96
 */
public final class MapIndex {

    /**
     * The map region this index identifies.
     */
    private final Region region;

    /**
     * The map tile file id.
     */
    private final int tileFileId;

    /**
     * The map object file id.
     */
    private final int objectFileId;

    /**
     * If the region detailed by this index is a priority region.
     */
    private final boolean priority;

    /**
     * Creates a new {@link MapIndex}.
     *
     * @param region The map region this index identifies.
     * @param tileFileId The map tile file id.
     * @param objectFileId The map object file id.
     * @param priority If the region detailed by this index is a priority region.
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
     * @return The map region this index identifies.
     */
    public Region getRegion() {
        return region;
    }

    /**
     * @return The map tile file id.
     */
    public int getTileFileId() {
        return tileFileId;
    }

    /**
     * @return The map object file id.
     */
    public int getObjectFileId() {
        return objectFileId;
    }

    /**
     * @return If the region detailed by this index is a priority region.
     */
    public boolean isPriority() {
        return priority;
    }
}
