package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mob.controller.PlayerController;

/**
 * Represents an {@link Event} type that may be intercepted, modified, or cancelled by a {@link PlayerController}
 * before being posted.
 * <p>
 * When an event implements this interface, it signals that the player's active controller has authority to prevent
 * the event from executing.
 * <p>
 * This is commonly used for:
 * <ul>
 *     <li>Restricting actions during minigames.</li>
 *     <li>Contextual modifications.</li>
 *     <li>Enforcing contextual gameplay rules.</li>
 * </ul>
 * <p>
 * This interface does not define behavior directly; it acts as a semantic contract recognized by the event dispatch
 * system.
 *
 * @author lare96
 */
public interface ControllableEvent {

}
