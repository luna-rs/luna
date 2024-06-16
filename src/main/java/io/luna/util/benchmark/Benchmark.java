package io.luna.util.benchmark;

import java.time.Duration;
import java.time.Instant;

/**
 * Represents the recorded duration of an event for measuring performance.
 *
 * @author lare96
 */
public final class Benchmark {

    /**
     * The type of benchmark.
     */
    private final BenchmarkType type;

    /**
     * The duration of the benchmark.
     */
    private final Duration elapsed;

    /**
     * The timestamp of a benchmark.
     */
    private final Instant timestamp = Instant.now();

    /**
     * Creates a new {@link Benchmark}.
     *
     * @param type The type of benchmark.
     * @param elapsed The duration of the benchmark.
     */
    public Benchmark(BenchmarkType type, Duration elapsed) {
        this.type = type;
        this.elapsed = elapsed;
    }

    /**
     * @return The type of benchmark.
     */
    public BenchmarkType getType() {
        return type;
    }

    /**
     * @return The duration of the benchmark.
     */
    public Duration getElapsed() {
        return elapsed;
    }

    /**
     * @return The timestamp of a benchmark.
     */
    public Instant getTimestamp() {
        return timestamp;
    }
}
