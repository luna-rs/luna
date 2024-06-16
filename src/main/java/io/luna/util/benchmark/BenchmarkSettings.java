package io.luna.util.benchmark;

import java.util.Set;

/**
 * Holds settings parsed from the "benchmark" section in {@code ./data/luna.json} file.
 *
 * @author lare96
 */
public final class BenchmarkSettings {

    private final int frequencyMinutes;
    private final Set<BenchmarkType> types;

    /**
     * How often benchmark reports will be printed.
     */
    public int frequencyMinutes() {
        return frequencyMinutes;
    }

    /**
     * The type of benchmark reports that will be printed.
     */
    public Set<BenchmarkType> types() {
        return types;
    }

    // Never used
    private BenchmarkSettings(int frequencyMinutes, Set<BenchmarkType> types) {
        this.frequencyMinutes = frequencyMinutes;
        this.types = types;
    }
}
