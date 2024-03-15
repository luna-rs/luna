package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;

/**
 * An {@link Event} sent when the player's region ID changes.
 *
 * @author lare96
 */
public final class RegionIdChangedEvent extends PlayerEvent {

    /**
     * The old position.
     */
    private final Position oldPos;

    /**
     * The new position.
     */
    private final Position newPos;

    /**
     * The old region ID.
     */
    private final int oldId;

    /**
     * The new region ID.
     */
    private final int newId;

    /**
     * Creates a new {@link RegionIdChangedEvent}.
     *
     * @param player The player.
     * @param oldPos The old position.
     * @param newPos The new position.
     * @param oldId The old region ID.
     * @param newId The new region ID.
     */
    public RegionIdChangedEvent(Player player, Position oldPos, Position newPos, int oldId, int newId) {
        super(player);
        this.oldPos = oldPos;
        this.newPos = newPos;
        this.oldId = oldId;
        this.newId = newId;
    }

    /**
     * @return The old position.
     */
    public Position getOldPos() {
        return oldPos;
    }

    /**
     * @return The new position.
     */
    public Position getNewPos() {
        return newPos;
    }

    /**
     * @return The old region ID.
     */
    public int getOldId() {
        return oldId;
    }

    /**
     * @return The new region ID.
     */
    public int getNewId() {
        return newId;
    }
}
