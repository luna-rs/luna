package io.luna.game;

import com.google.common.base.Stopwatch;
import io.luna.game.model.World;

import java.time.Duration;

/**
 * A model that computes the elapsed ticks between two points in time. Tick timing should be used strictly for game based
 * timing operations, otherwise {@link Stopwatch} should be used.
 *
 * @author lare96
 */
public class TickTimer implements Comparable<Long> {

    /**
     * The world.
     */
    protected final World world;

    /**
     * A snapshot of the world's tick at a certain point in time.
     */
    private long snapshot = -1;

    /**
     * Creates a new {@link TickTimer} with {@code initialDurationTicks}.
     *
     * @param world The world.
     */
    public TickTimer(World world) {
        this.world = world;
    }

    @Override
    public final int compareTo(Long o) {
        return Long.compare(getDurationTicks(), o);
    }

    @Override
    public final String toString() {
        return Long.toString(getDurationTicks());
    }

    /**
     * Computes the current duration, in ticks. Use {@link #getDuration()} if you'd like the duration in a different
     * time unit.
     */
    public final long getDurationTicks() {
        if (snapshot == -1) {
            throw new IllegalStateException("This timer has not been started!");
        }
        return world.getCurrentTick() - snapshot;
    }

    /**
     * Converts {@link #getDurationTicks()} ticks to an instance of {@code Duration}. This function assumes that each tick has completed in
     * under 600ms (and so is only an approximation).
     *
     * @return The duration instance.
     */
    public final Duration getDuration() {
        return Duration.ofMillis(getDurationTicks() * 600);
    }

    /**
     * Resets the current duration ({@link #getDurationTicks()}) to 0 ticks.
     */
    public TickTimer reset() {
        snapshot = -1;
        return this;
    }

    public TickTimer start() {
        snapshot = world.getCurrentTick();
        return this;
    }

    public boolean isRunning() {
        return snapshot != -1;
    }
}