package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.Entity;
import io.luna.game.model.Position;

/**
 * An {@link Event} implementation sent whenever an {@link Entity} changes its {@link Position}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PositionChangeEvent extends Event {

    /**
     * The old {@link Position} of the {@link Entity}.
     */
    private final Position oldPosition;

    /**
     * The new {@link Position} of the {@link Entity}.
     */
    private final Position newPosition;

    /**
     * The {@link Entity} changing its {@link Position}.
     */
    private final Entity entity;

    /**
     * Creates a new {@link PositionChangeEvent}.
     *
     * @param oldPosition The old {@link Position} of the {@link Entity}.
     * @param newPosition The new {@link Position} of the {@link Entity}.
     * @param entity The {@link Entity} changing its {@link Position}.
     */
    public PositionChangeEvent(Position oldPosition, Position newPosition, Entity entity) {
        this.oldPosition = oldPosition;
        this.newPosition = newPosition;
        this.entity = entity;
    }

    /**
     * @return The old {@link Position} of the {@link Entity}.
     */
    public Position getOldPosition() {
        return oldPosition;
    }

    /**
     * @return The new {@link Position} of the {@link Entity}.
     */
    public Position getNewPosition() {
        return newPosition;
    }

    /**
     * @return The {@link Entity} changing its {@link Position}.
     */
    public Entity getEntity() {
        return entity;
    }
}
