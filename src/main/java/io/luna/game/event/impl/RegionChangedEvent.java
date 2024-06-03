package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.Position;
import io.luna.game.model.Region;
import io.luna.game.model.mob.Player;


/**
 * An {@link Event} sent when a player enters a new {@link Region}.
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
     * @param player The player.
     * @param oldPos The old position.
     * @param newPos The new position.
     * @param oldId The old region ID.
     * @param newId The new region ID.
     */
    public RegionChangedEvent(Player player, Region oldRegion, Region newRegion) {
        super(player);
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
