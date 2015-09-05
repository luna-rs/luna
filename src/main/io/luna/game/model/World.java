package io.luna.game.model;

import io.luna.game.model.region.RegionManager;

/**
 * Manages the various types in the {@code io.luna.game.model} package and
 * subpackages.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class World {

    /**
     * The {@link RegionManager} that manages region caching.
     */
    private final RegionManager regions = new RegionManager();

    /**
     * @return The {@link RegionManager} instance.
     */
    public RegionManager getRegions() {
        return regions;
    }
}
