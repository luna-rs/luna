package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.WalkingQueue.Step;

/**
 * An event sent when a player walks.
 *
 * @author lare96
 */
public final class WalkingEvent extends PlayerEvent implements ControllableEvent {

    /**
     * The walking path.
     */
    private final Step[] path;

    /**
     * If the player is running.
     */
    private final boolean running;

    private final int pathSize;

    /**
     * Creates a new {@link WalkingEvent}.
     *
     * @param player The player.
     * @param path The path that the player will walk.
     * @param running If the player is running.
     */
    public WalkingEvent(Player player, Step[] path, boolean running, int pathSize) {
        super(player);
        this.path = path;
        this.running = running;
        this.pathSize = pathSize;
    }

    /**
     * @return The walking path.
     */
    public Step[] getPath() {
        return path;
    }

    /**
     * @return If the player is running.
     */
    public boolean isRunning() {
        return running;
    }

    public int getPathSize() {
        return pathSize;
    }
}
