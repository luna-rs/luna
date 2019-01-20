package io.luna.game.event.entity;

import io.luna.game.event.Event;
import io.luna.game.event.entity.mob.MobEvent;
import io.luna.game.model.Entity;

/**
 * An entity-based event. Not intended for interception.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class EntityEvent extends Event {

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