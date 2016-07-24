package io.luna.game.event.impl;

import io.luna.game.event.Event;

/**
 * An event implementation sent whenever a player logs in.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class LoginEvent extends Event {

    /**
     * Singleton instance.
     */
    public static final Event INSTANCE = new LoginEvent();

    /**
     * Private constructor to discourage external instantiation.
     */
    private LoginEvent() {
    }
}
