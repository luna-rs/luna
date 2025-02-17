package io.luna.util.benchmark;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ArrayListMultimap;
import io.luna.Luna;
import io.luna.game.GameSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Handles the recording and writing of all system {@link BenchmarkType} types.
 *
 * @author lare96
 */
public final class BenchmarkManager {

    /**
     * The date format for the main record section.
     */
    private static final DateTimeFormatter MAIN_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy @ HH:mm").
            withLocale(Locale.CANADA).withZone(ZoneId.systemDefault());

    /**
     * The date format for the individual record section.
     */
    private static final DateTimeFormatter RECORD_FORMATTER = DateTimeFormatter.ofPattern("HH:mm").
            withLocale(Locale.CANADA).withZone(ZoneId.systemDefault());

    /**
     * The date format for the record file.
     */
    private static final DateTimeFormatter FILE_FORMATTER = DateTimeFormatter.ofPattern("HH-mm").
            withLocale(Locale.CANADA).withZone(ZoneId.systemDefault());

    /**
     * The path to the top-level benchmark folder.
     */
    private static final Path RECORDS_PATH = Paths.get("data", "benchmark");

    static {
        // Creates the benchmark folder if it doesn't exist.
        if (!Files.exists(RECORDS_PATH)) {
            try {
                Files.createDirectory(RECORDS_PATH);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * A map of active benchmarks. Modified when {@link #startBenchmark(BenchmarkType)} is called.
     */
    private final Map<BenchmarkType, Stopwatch> active = new EnumMap<>(BenchmarkType.class);
// todo thread safety for ^v
    /**
     * A map of completed benchmarks. Moved from {@link #active} when {@link #finishBenchmark(BenchmarkType)}
     * is called.
     */
    private final ArrayListMultimap<BenchmarkType, Benchmark> completed = ArrayListMultimap.create();

    /**
     * The benchmark service that will auto-print records.
     */
    private final BenchmarkService service = new BenchmarkService(this);

    /**
     * Starts the benchmark for {@code type}. Restarts the benchmark if one has already started.
     *
     * @param type The benchmark to start.
     */
    public void startBenchmark(BenchmarkType type) {
        GameSettings gameSettings = Luna.settings().game();
        if (gameSettings.betaMode()) {
            active.put(type, Stopwatch.createStarted());
        }
    }

    /**
     * Finishes the benchmark for {@code type}. Does nothing if there is no active benchmark.
     *
     * @param type The benchmark to finish.
     */
    public void finishBenchmark(BenchmarkType type) {
        Stopwatch stopwatch = active.get(type);
        if (stopwatch != null) {
            Duration elapsed = stopwatch.elapsed();
            completed.put(type, new Benchmark(type, elapsed));
        }
    }

    /**
     * Writes a single {@code type} record to the disk.
     *
     * @param type The type of benchmark to write.
     */
    public void writeRecord(BenchmarkType type) {
        try {
            Instant now = Instant.now();
            Path recordPath = RECORDS_PATH.resolve(type.getFormattedName() + " " + FILE_FORMATTER.format(now) + ".txt");
            if (!Files.exists(recordPath)) {
                Files.createFile(recordPath);
            }
            Files.writeString(recordPath, buildRecord(type, now));
        } catch (IOException e) {
            logger.catching(e);
        }
    }

    /**
     * Write all benchmark records to the disk.
     */
    public void writeAllRecords() {
        for (BenchmarkType type : BenchmarkType.VALUES) {
            if (type == BenchmarkType.ALL) {
                continue;
            }
            writeRecord(type);
        }
    }

    /**
     * Clears a single pending and all recorded benchmarks for {@code type}.
     *
     * @param type The type to clear data for.
     */
    public void clear(BenchmarkType type) {
        active.remove(type);
        completed.removeAll(type);
    }

    /**
     * Clears all pending and recorded benchmarks for all {@link BenchmarkType} types.
     */
    public  void clearAll() {
        active.clear();
        completed.clear();
    }

    /**
     * Builds a record for the collection of benchmarks.
     *
     * @param type The benchmark type.
     * @param now An instant representing this point in time.
     * @return The generated record for the benchmarks
     */
    private String buildRecord(BenchmarkType type, Instant now) {
        TimeUnit unit = type.getTimeUnit();
        List<Benchmark> benchmarks = completed.get(type);
        int size = benchmarks.size();
        long averageTime = 0;
        int current = 1;
        StringBuilder recordDisplay = new StringBuilder();
        for (Benchmark nextBenchmark : benchmarks) {
            Instant timestamp = nextBenchmark.getTimestamp();
            long time = unit.convert(nextBenchmark.getElapsed());
            recordDisplay.append(RECORD_FORMATTER.format(timestamp)).
                    append(" | #").append(current++).append(": ").append(time).append(' ').append(unit).append('\n');
            averageTime += unit.convert(nextBenchmark.getElapsed());
        }
        averageTime = averageTime / size;
        return "RECORD [" + type + "] | " + MAIN_FORMATTER.format(now) + "\n\n" +
                "Num. of benchmarks: " + size + '\n' +
                "Avg. time: " + averageTime + ' ' + type.getTimeUnit() + "\n\n" +
                recordDisplay;
    }

    /**
     * @return The benchmark service that will auto-print records.
     */
    public BenchmarkService getService() {
        return service;
    }
}
