package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.Entity;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;

/**
 * An entity-based event. Not intended for interception.
 *
 * @author lare96 <http://github.org/lare96>
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
    public Entity entity() {
        return entity;
    }

    /**
     * Returns the entity as a mob.
     */
    public Mob asMob() {
        return (Mob) entity;
    }

    /**
     * Returns the entity as a non-player.
     */
    public Npc asNpc() {
        return (Npc) entity;
    }

    /**
     * Returns the entity as a player.
     */
    public Player asPlr() {
        return (Player) entity;
    }

    /**
     * Returns the entity as an object.
     */
    public GameObject asObject() {
        return (GameObject) entity;
    }

    /**
     * Returns the entity as a ground item.
     */
    public GroundItem asItem() {
        return (GroundItem) entity;
    }
}