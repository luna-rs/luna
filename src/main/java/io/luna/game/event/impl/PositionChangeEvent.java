package io.luna.game.event.impl;

import io.luna.game.model.Entity;
import io.luna.game.model.Position;

/**
 * An event sent whenever an entity changes its position.
 * <p>
 * Please note that the new position of the entity will have <strong>not</strong> been
 * set yet when this method is posted, use the values within this class instead of {@code getPosition()}
 * from {@code Entity}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PositionChangeEvent extends EntityEvent {

    /**
     * The old position.
     */
    private final Position oldPos;

    /**
     * The new position.
     */
    private final Position newPos;

    /**
     * Creates a new {@link PositionChangeEvent}.
     *
     * @param entity The entity.
     * @param oldPos The old position.
     * @param newPos The new new position.
     */
    public PositionChangeEvent(Entity entity, Position oldPos, Position newPos) {
        super(entity);
        this.oldPos = oldPos;
        this.newPos = newPos;
    }

    @Override
    public boolean terminate() {
        throw new IllegalStateException("This event type (PositionChangeEvent) cannot be terminated.");
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
}
