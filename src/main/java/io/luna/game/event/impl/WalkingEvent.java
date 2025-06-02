package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.WalkingQueue.Step;

import java.util.Deque;

/**
 * An event sent when a player walks.
 *
 * @author lare96
 */
public final class WalkingEvent extends PlayerEvent implements ControllableEvent {

    /**
     * The walking path.
     */
    private final Deque<Step> path;

    /**
     * If the player is running.
     */
    private final boolean running;

    /**
     * The path size.
     */
    private final int pathSize;

    /**
     * The opcode.
     */
    private final int opcode;

    /**
     * Creates a new {@link WalkingEvent}.
     *
     * @param player The player.
     * @param path The path that the player will walk.
     * @param running If the player is running.
     * @param pathSize The path size.
     * @param opcode The opcode.
     */
    public WalkingEvent(Player player, Deque<Step> path, boolean running, int pathSize, int opcode) {
        super(player);
        this.path = path;
        this.running = running;
        this.pathSize = pathSize;
        this.opcode = opcode;
    }

    /**
     * @return The walking path.
     */
    public Deque<Step> getPath() {
        return path;
    }

    /**
     * @return If the player is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * @return The path size.
     */
    public int getPathSize() {
        return pathSize;
    }

    /**
     * @return The opcode.
     */
    public int getOpcode() {
        return opcode;
    }
}
