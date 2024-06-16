package io.luna.util.benchmark;

import com.google.common.util.concurrent.AbstractScheduledService;
import io.luna.Luna;
import io.luna.LunaRuntime;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.Set;

/**
 * An {@link AbstractScheduledService} that will print benchmark records if needed
 */
public final class BenchmarkService extends AbstractScheduledService {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The benchmark manager.
     */
    private final BenchmarkManager benchmarkManager;

    /**
     * Creates a new {@link BenchmarkService}.
     *
     * @param benchmarkManager The benchmark manager.
     */
    public BenchmarkService(BenchmarkManager benchmarkManager) {
        this.benchmarkManager = benchmarkManager;
    }

    @Override
    protected void startUp() throws Exception {
        if (Luna.settings().game().runtimeMode() != LunaRuntime.BENCHMARK) {
            // Terminate if not in benchmark runtime mode.
            stopAsync();
        }
    }

    @Override
    protected void runOneIteration() throws Exception {
        Set<BenchmarkType> types = Luna.settings().benchmark().types();
        if (types.isEmpty()) {
            logger.error("'types' under 'benchmark' in luna.json cannot be empty. Please use 'ALL' or specify which benchmarks you would like recorded. BenchmarkService will now shut down...");
            stopAsync();
        } else if (types.contains(BenchmarkType.ALL)) {
            benchmarkManager.writeAllRecords();
        } else {
            for (BenchmarkType benchmarkType : types) {
                benchmarkManager.writeRecord(benchmarkType);
            }
        }
    }

    @Override
    protected Scheduler scheduler() {
        int minutes = Luna.settings().benchmark().frequencyMinutes();
        return Scheduler.newFixedRateSchedule(Duration.ofMinutes(minutes),
                Duration.ofMinutes(minutes));
    }

    @Override
    protected void shutDown() throws Exception {
        benchmarkManager.clearAll();
    }
}
