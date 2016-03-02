package io.luna.game.event.impl;

import io.luna.game.event.Event;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.WalkingQueue.Step;

/**
 * An {@link Event} implementation sent when an {@link Player} walks.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class WalkingEvent extends Event {

    /**
     * The path that the {@link Player} will walk.
     */
    private final Step[] path;

    /**
     * If the {@link Player} is running.
     */
    private final boolean running;

    /**
     * Creates a new {@link WalkingEvent}.
     *
     * @param path The path that the {@link Player} will walk.
     * @param running If the {@link Player} is running.
     */
    public WalkingEvent(Step[] path, boolean running) {
        this.path = path;
        this.running = running;
    }

    /**
     * @return The path that the {@link Player} will walk.
     */
    public Step[] getPath() {
        return path;
    }

    /**
     * @return {@code true} if the {@link Player} is running, {@code false} otherwise.
     */
    public boolean getRunning() {
        return running;
    }
}
