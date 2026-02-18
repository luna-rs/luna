package io.luna.game;

import com.google.common.base.Stopwatch;
import io.luna.game.model.World;

import java.time.Duration;

/**
 * A lightweight timer that measures elapsed time in <em>game ticks</em> using {@link World#getCurrentTick()}.
 * <p>
 * Use this for gameplay timing (cooldowns, throttles, “X ticks since…”, etc.). For real wall-clock timing
 * (profiling, IO latency, external services), prefer {@link Stopwatch}.
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>{@link #start()} captures the current world tick as the baseline.</li>
 *   <li>{@link #getDurationTicks()} returns {@code worldTick - baselineTick}.</li>
 *   <li>{@link #reset()} clears the baseline (timer becomes “not started”).</li>
 * </ul>
 * <p>
 * <strong>Threading:</strong> This reads {@link World#getCurrentTick()}. It’s intended to be used on the game thread
 * where the tick counter is advanced. If you read it from other threads, you’ll get whatever visibility guarantees
 * {@code currentTick} provides.
 *
 * @author lare96
 */
public class TickTimer implements Comparable<Long> {

    /**
     * World providing the authoritative tick counter.
     */
    protected final World world;

    /**
     * Baseline tick captured at {@link #start()}, or {@code -1} when not running.
     */
    private long snapshot = -1;

    /**
     * Creates a new {@link TickTimer} bound to {@code world}.
     *
     * @param world The world providing the tick counter.
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
     * Returns elapsed time since {@link #start()} in ticks.
     *
     * @return Elapsed ticks.
     * @throws IllegalStateException If {@link #start()} has not been called.
     */
    public final long getDurationTicks() {
        if (snapshot == -1) {
            throw new IllegalStateException("This timer has not been started!");
        }
        return world.getCurrentTick() - snapshot;
    }

    /**
     * Returns elapsed time as a {@link Duration}.
     * <p>
     * This converts ticks to milliseconds assuming a nominal 600ms tick. It’s an approximation (real tick duration
     * can drift under load).
     *
     * @return Elapsed time as a {@link Duration}.
     * @throws IllegalStateException If {@link #start()} has not been called.
     */
    public final Duration getDuration() {
        return Duration.ofMillis(getDurationTicks() * 600);
    }

    /**
     * Stops the timer and clears the baseline tick.
     * <p>
     * After calling this, the timer is considered “not started” until {@link #start()} is called again.
     *
     * @return {@code this} for chaining.
     */
    public TickTimer reset() {
        snapshot = -1;
        return this;
    }

    /**
     * Starts (or restarts) the timer by capturing the current world tick as the baseline.
     *
     * @return {@code this} for chaining.
     */
    public TickTimer start() {
        snapshot = world.getCurrentTick();
        return this;
    }

    /**
     * Returns whether this timer has been started and not reset.
     */
    public boolean isRunning() {
        return snapshot != -1;
    }
}
