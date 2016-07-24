package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mobile.WalkingQueue.Step;

/**
 * An event implementation sent when an player walks.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class WalkingEvent extends Event {

    /**
     * The path that the player will walk.
     */
    private final Step[] path;

    /**
     * If the player is running.
     */
    private final boolean running;

    /**
     * Creates a new {@link WalkingEvent}.
     *
     * @param path The path that the player will walk.
     * @param running If the player is running.
     */
    public WalkingEvent(Step[] path, boolean running) {
        this.path = path;
        this.running = running;
    }

    /**
     * @return The path that the player will walk.
     */
    public Step[] getPath() {
        return path;
    }

    /**
     * @return {@code true} if the player is running, {@code false} otherwise.
     */
    public boolean getRunning() {
        return running;
    }
}
