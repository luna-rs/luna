package io.luna.game.model.mob;

import io.luna.game.model.Position;

import java.time.Duration;

/**
 * Tracks a player's NPC aggression tolerance state.
 * <p>
 * Tolerance is modeled using two remembered anchor positions:
 * <ul>
 *     <li>{@code oldest} - The older remembered anchor position.</li>
 *     <li>{@code newest} - The most recently remembered anchor position.</li>
 * </ul>
 * <p>
 * If the player moves more than 10 tiles away from both anchors, the anchors are rotated and the tolerance timer
 * is reset.
 *
 * @author lare96
 */
public class PlayerAggressionTolerance {

    /**
     * The player that owns this tolerance state.
     */
    private transient final Player player;

    /**
     * The older remembered aggression anchor position.
     */
    private Position oldest;

    /**
     * The newer remembered aggression anchor position.
     */
    private Position newest;

    /**
     * The number of game ticks elapsed since the current tolerance state began.
     */
    private int ticks;

    /**
     * Creates a new {@link PlayerAggressionTolerance} for the specified player.
     *
     * @param player The player that owns this tolerance state.
     */
    public PlayerAggressionTolerance(Player player) {
        this.player = player;
    }

    /**
     * Ensures this tolerance state has valid default anchor positions.
     * <p>
     * If either anchor is missing, both anchors are initialized to the player's current position and the timer is
     * reset.
     *
     * @return {@code true} if defaults were applied, otherwise {@code false}.
     */
    public boolean ensureDefaults() {
        if (oldest == null || newest == null) {
            oldest = player.getPosition();
            newest = player.getPosition();
            ticks = 0;
            return true;
        }
        return false;
    }

    /**
     * Loads tolerance data from another instance.
     * <p>
     * If {@code other} is {@code null}, this method does nothing.
     *
     * @param other The tolerance state to copy from.
     */
    public void load(PlayerAggressionTolerance other) {
        if (other != null) {
            oldest = other.oldest;
            newest = other.newest;
            ticks = other.ticks;
        }
    }

    /**
     * Advances the tolerance timer by one game tick.
     */
    public void process() {
        ticks++;
    }

    /**
     * Refreshes this tolerance state based on the player's current position.
     * <p>
     * If defaults are not needed and the player is more than 10 tiles away from both remembered anchors, the anchors
     * are rotated and the timer is reset.
     */
    public void refresh() {
        if (!ensureDefaults() && !player.isWithinDistance(oldest, 10) && !player.isWithinDistance(newest, 10)) {
            oldest = newest;
            newest = player.getPosition();
            ticks = 0;
        }
    }

    /**
     * @return The elapsed tolerance duration derived from {@link #ticks}.
     */
    public Duration getDuration() {
        return Duration.ofMillis(ticks * 600L);
    }

    /**
     * @return The elapsed tick count.
     */
    public int getTicks() {
        return ticks;
    }
}