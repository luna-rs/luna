package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.WalkingQueue.Step;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A {@link PlayerEvent} implementation sent when a player clicks to walk somewhere.
 *
 * @author lare96
 */
public final class WalkingEvent extends PlayerEvent implements ControllableEvent {

    /**
     * The source of the walking click.
     */
    public enum WalkingOrigin {

        /**
         * A movement command where the source is a click on the main game screen. Represented by a yellow X.
         */
        MAIN_SCREEN,

        /**
         * A movement command where the source is an interaction click (player, npc, object, ground item, etc).
         * Represented by a red X.
         */
        INTERACTION,

        /**
         * A movement command where the source is a minimap click. Represented by a red flag on the minimap.
         */
        MINIMAP;

        /**
         * Determines the correct {@link WalkingOrigin} based on the given {@code opcode}.
         *
         * @param opcode The opcode.
         * @return The correct walking origin.
         */
        public static WalkingOrigin forOpcode(int opcode) {
            switch (opcode) {
                case 213:
                    return MINIMAP;
                case 28:
                    return MAIN_SCREEN;
                case 247:
                    return INTERACTION;
                default:
                    throw new IllegalStateException("Invalid opcode [" + opcode + "]");
            }
        }
    }

    /**
     * The walking origin.
     */
    private final WalkingOrigin origin;

    /**
     * The {@code x} coordinate of the first step.
     */
    private final int firstStepX;

    /**
     * The {@code y} coordinate of the first step.
     */
    private final int firstStepY;

    /**
     * The path.
     */
    private final int[][] path;

    /**
     * The path size.
     */
    private final int pathSize;

    /**
     * If the player is running.
     */
    private final boolean running;

    /**
     * The steps to be sent to the walking queue.
     */
    private Deque<Step> steps;

    /**
     * Creates a new {@link WalkingEvent}.
     *
     * @param player The player.
     * @param origin The walking origin.
     * @param firstStepX The {@code x} coordinate of the first step.
     * @param firstStepY The {@code y} coordinate of the first step.
     * @param path The path.
     * @param pathSize The path size.
     * @param running If the player is running.
     */
    public WalkingEvent(Player player, WalkingOrigin origin, int firstStepX, int firstStepY, int[][] path,
                        int pathSize, boolean running) {
        super(player);
        this.origin = origin;
        this.firstStepX = firstStepX;
        this.firstStepY = firstStepY;
        this.path = path;
        this.pathSize = pathSize;
        this.running = running;
    }

    /**
     * @return The walking origin.
     */
    public WalkingOrigin getOrigin() {
        return origin;
    }

    /**
     * @return The {@code x} coordinate of the first step.
     */
    public int getFirstStepX() {
        return firstStepX;
    }

    /**
     * @return The {@code y} coordinate of the first step.
     */
    public int getFirstStepY() {
        return firstStepY;
    }

    /**
     * @return The path
     */
    public int[][] getPath() {
        return path;
    }

    /**
     * @return The path size.
     */
    public int getPathSize() {
        return pathSize;
    }

    /**
     * @return If the player is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * @return The walking path.
     */
    public Deque<Step> getSteps() {
        if (steps == null) {
            steps = new ArrayDeque<>(pathSize + 1);
            steps.add(new Step(firstStepX, firstStepY));
            for (int i = 0; i < pathSize; i++) {
                steps.add(new Step(path[i][0] + firstStepX, path[i][1] + firstStepY));
            }
        }
        return this.steps;
    }
}
