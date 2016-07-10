package io.luna.game.event.impl;

import io.luna.Server;
import io.luna.game.event.Event;

/**
 * An {@link Event} implementation sent when the {@link Server} launches.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ServerLaunchEvent extends Event {

    /**
     * Singleton instance.
     */
    public static final Event INSTANCE = new ServerLaunchEvent();

    /**
     * Private constructor to discourage external instantiation.
     */
    private ServerLaunchEvent() {
    }
}
