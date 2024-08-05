package io.luna.game.event.impl;

import io.luna.game.action.InteractionAction;
import io.luna.game.event.Event;
import io.luna.game.model.Entity;

/**
 * Represents an {@link Event} type that needs to be posted within an {@link InteractionAction}. Events like this are
 * not posted until the interaction can be successfully completed.
 *
 * @author lare96
 */
public interface InteractableEvent {

    /**
     * @return The target of this interactable event.
     */
    Entity target();
}
