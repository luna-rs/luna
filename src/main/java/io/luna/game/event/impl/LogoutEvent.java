package io.luna.game.event.impl;

import io.luna.game.event.Event;

/**
 * An event implementation sent whenever a player logs out.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class LogoutEvent extends Event {

    /**
     * Singleton instance.
     */
    public static final Event INSTANCE = new LogoutEvent();

    /**
     * Private constructor to discourage external instantiation.
     */
    private LogoutEvent() {
    }
}
