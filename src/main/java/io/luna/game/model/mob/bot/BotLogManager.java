package io.luna.game.model.mob.bot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

/**
 * A lightweight asynchronous logger that maintains a bounded ring buffer of recent {@link Bot} log entries for
 * debugging and analysis.
 * <p>
 * Each bot maintains its own {@code BotLogManager} instance. Log messages are stored in-memory up to a fixed
 * capacity, and optionally streamed in real time to a persistent file on disk. This enables developers to observe
 * a bot’s decision-making process, behavior changes, or script transitions without flooding the global server log.
 * <p>
 * The manager operates in two distinct modes:
 * <ul>
 *     <li><b>Buffered mode</b> – Messages are accumulated in memory. When {@link #writeBuffer()} is called, the
 *     buffer is flushed to disk.</li>
 *     <li><b>Streaming mode</b> – Messages are appended directly to the log file as they are generated, in addition
 *     to being stored in memory.</li>
 * </ul>
 *
 * @author lare96
 */
public final class BotLogManager {

    /**
     * Represents a single timestamped log entry.
     */
    private static final class BotLogEntry {

        /**
         * The timestamp formatter used for each entry.
         */
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("MMM d, hh:mm:s a")
                .withZone(ZoneId.of("UTC"));

        /**
         * The time the log was recorded.
         */
        private final Instant timestamp;

        /**
         * The text of the log message.
         */
        private final String text;

        /**
         * Creates a new {@link BotLogEntry}.
         *
         * @param timestamp The timestamp of the log.
         * @param text The log message.
         */
        public BotLogEntry(Instant timestamp, String text) {
            this.timestamp = timestamp;
            this.text = text;
        }

        /**
         * Returns this entry formatted for file output.
         *
         * @return A formatted message string.
         */
        public String getFormattedMessage() {
            try {
                return "[" + FORMATTER.format(timestamp) + "] " + text + "\n";
            } catch (Exception e) {
                logger.catching(e);
                return "null\n";
            }
        }
    }

    /**
     * The logger used for internal error reporting.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The maximum number of log entries retained in memory.
     */
    private static final int BUFFER_CAPACITY = 50;

    /**
     * The file path this bot’s logs will be written to.
     */
    private final Path path;

    /**
     * The bounded queue holding the most recent log entries.
     */
    private final Queue<BotLogEntry> buffer;

    /**
     * The owning {@link Bot}.
     */
    private final Bot bot;

    /**
     * Whether logs are currently streamed to disk as they are added.
     */
    private volatile boolean streaming;

    /**
     * Creates a new {@link BotLogManager} for the specified bot.
     *
     * @param bot The bot whose activity will be logged.
     */
    public BotLogManager(Bot bot) {
        this.bot = bot;
        this.path = Paths.get("data", "game", "bots", "logs",
                bot.getUsername().toLowerCase() + ".txt");
        this.buffer = new ArrayDeque<>(BUFFER_CAPACITY);
    }

    /**
     * Adds a new log message to the in-memory buffer and, if streaming mode is active, asynchronously appends it
     * to the bot’s log file.
     * <p>
     * If the buffer has reached its maximum capacity, the oldest entry will be discarded to make room for the new one.
     *
     * @param text The log message to record.
     */
    public void log(String text) {
        if (buffer.size() >= BUFFER_CAPACITY) {
            buffer.poll();
        }
        BotLogEntry entry = new BotLogEntry(Instant.now(), text);
        buffer.add(entry);
        if (streaming) {
            bot.getService().submit(() -> {
                try {
                    Files.writeString(path, entry.getFormattedMessage(),
                            StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                } catch (IOException e) {
                    logger.catching(e);
                }
            });
        }
    }

    /**
     * Writes the current buffer contents to the log file, overwriting any existing content.
     * <p>
     * This method immediately returns {@code false} if streaming mode is enabled, as streaming bots continuously
     * write to file and do not require a manual flush.
     *
     * @return A {@link CompletableFuture} that completes with {@code true} if the buffer was successfully
     * written, or {@code false} otherwise.
     */
    public CompletableFuture<Boolean> writeBuffer() {
        if (!streaming) {
            return bot.getService().submit(() -> {
                try {
                    Files.writeString(path, exportLogs(),
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.CREATE);
                    return true;
                } catch (IOException e) {
                    logger.catching(e);
                    return false;
                }
            });
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Exports the contents of the internal buffer as a concatenated string.
     *
     * @return A textual representation of all recent logs.
     */
    private String exportLogs() {
        StringJoiner sj = new StringJoiner("\n");
        for (BotLogEntry entry : buffer) {
            sj.add(entry.getFormattedMessage());
        }
        return sj.toString();
    }

    /**
     * Toggles live-streaming mode for this log manager.
     * <p>
     * When enabled, all future log entries are immediately written to disk as they are generated. When disabled,
     * logs accumulate in memory until {@link #writeBuffer()} is invoked.
     *
     * @param streaming {@code true} to enable streaming mode, {@code false} to
     * revert to buffered mode.
     */
    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }
}
