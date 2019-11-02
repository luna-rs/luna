package io.luna.game.event.impl;

import io.luna.game.event.Event;

/**
 * An event sent when the server launches.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ServerLaunchEvent extends Event {

    /**
     * Singleton instance.
     */
    public static final Event INSTANCE = new ServerLaunchEvent();

    /**
     * Private constructor.
     */
    private ServerLaunchEvent() {
        super();
    }
}
