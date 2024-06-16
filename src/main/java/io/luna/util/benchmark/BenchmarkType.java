package io.luna.util.benchmark;

import com.google.common.collect.ImmutableSet;

import java.util.concurrent.TimeUnit;

/**
 * Represents a label for a {@link Benchmark} that describes its purpose.
 *
 * @author lare96
 */
public enum BenchmarkType {

    /**
     * Represents all benchmark types in one element.
     */
    ALL,

    /**
     * A player and NPC update benchmark.
     */
    MOB_UPDATING,

    /**
     * A benchmark of the entire game loop.
     */
    GAME_LOOP;

    /**
     * An immutable copy of {@link #values()}.
     */
    public static final ImmutableSet<BenchmarkType> VALUES = ImmutableSet.copyOf(values());

    /**
     * The unit that this benchmark is represented in, {@link TimeUnit#MILLISECONDS} by default.
     */
    private final TimeUnit timeUnit;

    /**
     * Creates a new {@link BenchmarkType}.
     *
     * @param timeUnit The unit that this benchmark is represented in.
     */
    BenchmarkType(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    /**
     * Creates a new {@link BenchmarkType} with the default time unit.
     */
    BenchmarkType() {
        this(TimeUnit.MILLISECONDS);
    }

    /**
     * @return The lowercase name of this type.
     */
    public String getFormattedName() {
        return name().toLowerCase();
    }

    /**
     * @return The unit that this benchmark is represented in.
     */
    public TimeUnit getTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }
}
