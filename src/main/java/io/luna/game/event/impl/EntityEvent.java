package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.Entity;

/**
 * An entity-based event. Not intended for interception.
 *
 * @author lare96
 */
class EntityEvent extends Event {

    /**
     * The entity.
     */
    protected final Entity entity;

    /**
     * Creates a new {@link MobEvent}.
     *
     * @param entity The entity.
     */
    public EntityEvent(Entity entity) {
        this.entity = entity;
    }

    /**
     * @return The entity.
     */
    public Entity getEntity() {
        return entity;
    }
}