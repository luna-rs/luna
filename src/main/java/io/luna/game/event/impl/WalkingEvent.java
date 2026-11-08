package io.luna.game.event.impl;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.movement.WalkingQueue.PlannedPath;

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
     * The steps to be sent to the walking queue.
     */
    private final Deque<Position> steps;
    /**
     * The walking origin.
     */
    private final WalkingOrigin origin;

    /**
     * If the player is running.
     */
    private final boolean running;

    /**
     * The validated route, retained across controller and overlay callbacks before it is queued.
     */
    private PlannedPath plannedPath;


    /**
     * Creates a new {@link WalkingEvent}.
     *
     * @param player The player.
     *
     * @param origin The walking origin.
     * @param running If the player is running.
     */
    public WalkingEvent(Player player, Deque<Position> steps, WalkingOrigin origin, boolean running) {
        super(player);
        this.steps = steps;
        this.origin = origin;
        this.running = running;
    }

    /**
     * @return The walking origin.
     */
    public WalkingOrigin getOrigin() {
        return origin;
    }

    /**
     * @return If the player is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * @return The walking path converted from a nested array to a queue of positions.
     */
    public Deque<Position> getSteps() {
        return steps;
    }

    /**
     * Returns the validated route, or {@code null} for an event that has not passed packet validation.
     *
     * @return The prepared route.
     */
    public PlannedPath getPlannedPath() {
        return plannedPath;
    }

    /**
     * Retains a successfully validated route without changing the client's original compressed waypoints.
     *
     * @param plannedPath The prepared route.
     */
    public void setPlannedPath(PlannedPath plannedPath) {
        this.plannedPath = plannedPath;
    }
}
