package io.luna.game.action;

/**
 * Marks travel actions whose owned locks and visual state must be released before the death sequence acquires them.
 * These actions are interrupted synchronously when death is submitted, rather than during a later action phase.
 *
 * @author lare96
 */
public interface DeathInterruptible {
}
