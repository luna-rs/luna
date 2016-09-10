package io.luna.game.event.impl;

import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.WalkingQueue.Step;

/**
 * An event sent when a player walks.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class WalkingEvent extends PlayerEvent {

    /**
     * The walking path.
     */
    private final Step[] path;

    /**
     * If the player is running.
     */
    private final boolean running;

    /**
     * Creates a new {@link WalkingEvent}.
     *
     * @param player The player.
     * @param path The path that the player will walk.
     * @param running If the player is running.
     */
    public WalkingEvent(Player player, Step[] path, boolean running) {
        super(player);
        this.path = path;
        this.running = running;
    }

    /**
     * @return The walking path.
     */
    public Step[] path() {
        return path;
    }

    /**
     * @return If the player is running.
     */
    public boolean running() {
        return running;
    }
}
