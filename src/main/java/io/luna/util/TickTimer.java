package io.luna.util;

import com.google.common.base.Stopwatch;
import io.luna.game.model.World;

import java.time.Duration;

/**
 * A model that computes the elapsed ticks between two points in time. Tick timing should be used strictly for game based
 * timing operations, otherwise {@link Stopwatch} should be used.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class TickTimer implements Comparable<Long> {

    /**
     * The world.
     */
    private final World world;

    /**
     * A snapshot of the world's tick at a certain point in time.
     */
    private long snapshot;

    /**
     * Creates a new {@link TickTimer} with {@code initialDurationTicks}.
     *  @param world The world.
     * @param initialDurationTicks The initial duration, in ticks.
     */
    public TickTimer(World world, long initialDurationTicks) {
        this.world = world;
        snapshot = world.getCurrentTick() - initialDurationTicks;
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
     * Reset the current duration to {@link World#getCurrentTick()} ticks.
     */
    public final void reset() {
        snapshot = world.getCurrentTick();
    }
}