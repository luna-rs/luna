package io.luna.game.event.impl;

import io.luna.game.model.Region;
import io.luna.game.model.mob.Player;

/**
 * An event sent when the player changes regions.
 *
 * @author lare96
 */
public final class RegionChangedEvent extends PlayerEvent {

    /**
     * The old region.
     */
    private final Region oldRegion;

    /**
     * The new region.
     */
    private final Region newRegion;

    /**
     * Creates a new {@link RegionChangedEvent}.
     *
     * @param plr The player.
     * @param oldRegion The old region.
     * @param newRegion The new region.
     */
    public RegionChangedEvent(Player plr, Region oldRegion, Region newRegion) {
        super(plr);
        this.oldRegion = oldRegion;
        this.newRegion = newRegion;
    }

    /**
     * @return The old region.
     */
    public Region getOldRegion() {
        return oldRegion;
    }

    /**
     * @return The new region.
     */
    public Region getNewRegion() {
        return newRegion;
    }
}
