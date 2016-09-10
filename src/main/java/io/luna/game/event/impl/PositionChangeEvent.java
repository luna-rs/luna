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
    private final Position oldPosition;

    /**
     * The new position.
     */
    private final Position newPosition;

    /**
     * Creates a new {@link PositionChangeEvent}.
     *
     * @param entity The entity.
     * @param oldPosition The old position.
     * @param newPosition The new new position.
     */
    public PositionChangeEvent(Entity entity, Position oldPosition, Position newPosition) {
        super(entity);
        this.oldPosition = oldPosition;
        this.newPosition = newPosition;
    }

    /**
     * @return The old position.
     */
    public Position oldPosition() {
        return oldPosition;
    }

    /**
     * @return The new position.
     */
    public Position newPosition() {
        return newPosition;
    }
}
