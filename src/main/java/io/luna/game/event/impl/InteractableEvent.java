package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.event.EventListener;
import io.luna.game.model.Entity;
import io.luna.game.model.mob.interact.InteractionAction;

/**
 * Represents an {@link Event} type that require a target to be interacted with, before they are posted.
 * <p>
 * Events implementing this interface are not dispatched immediately. Instead, they are wrapped in an
 * {@link InteractionAction}, which handles movement and distance checks. The event is only posted once the interaction
 * requirements (such as proximity) are successfully satisfied.
 * <p>
 * Interaction requirements are determined by {@link EventListener} types within scripts.
 * <p>
 * Typical use cases include:
 * <ul>
 *     <li>Clicking an NPC that requires walking to it first.</li>
 *     <li>Interacting with an object that must be reached.</li>
 *     <li>Casting a spell on an entity.</li>
 * </ul>
 *
 * @author lare96
 */
public interface InteractableEvent {

    /**
     * Returns the interaction target for this event.
     * <p>
     * The returned {@link Entity} is used by the {@link InteractionAction} to determine pathfinding, proximity checks,
     * and completion conditions.
     *
     * @return The target entity of the interaction.
     */
    Entity target();
}
