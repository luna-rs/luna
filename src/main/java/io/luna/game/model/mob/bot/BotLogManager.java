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
import java.util.Queue;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.util.Objects.requireNonNullElse;

/**
 * Manages recent and live log output for a single {@link Bot}.
 * <p>
 * Each bot owns its own {@code BotLogManager}. Log entries are always stored in a small in-memory buffer so recent
 * bot activity can be inspected without scanning the global server log. Depending on the configured
 * {@link BotStreamType}, log messages can also be sent to chat, written to a file, ignored externally, or streamed
 * to both destinations.
 * <p>
 * The in-memory buffer is bounded. Once the buffer reaches its capacity, the oldest entry is removed before a new
 * entry is added. This keeps the logger lightweight even when many bots are active.
 *
 * @author lare96
 */
public final class BotLogManager {

    /**
     * Defines where bot log messages should be streamed when they are recorded.
     */
    public enum BotStreamType {

        /**
         * Sends log messages through the bot's in-game chat only.
         */
        CHAT,

        /**
         * Appends log messages to the bot's log file only.
         */
        FILE,

        /**
         * Keeps log messages in the in-memory buffer only.
         */
        NONE,

        /**
         * Sends log messages to both in-game chat and the bot's log file.
         */
        ALL;

        /**
         * Checks if this stream type should send log messages through bot chat.
         *
         * @return {@code true} if logs should be spoken by the bot.
         */
        public boolean isChatStream() {
            return this == CHAT || this == ALL;
        }

        /**
         * Checks if this stream type should write log messages to the bot's file.
         *
         * @return {@code true} if logs should be appended to the bot log file.
         */
        public boolean isFileStream() {
            return this == FILE || this == ALL;
        }
    }

    /**
     * Represents a single timestamped bot log entry.
     */
    private static final class BotLogEntry {

        /**
         * Formats log timestamps using UTC so bot logs are consistent across server environments.
         */
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("MMM d, hh:mm:s a")
                .withZone(ZoneId.of("UTC"));

        /**
         * The time this log entry was created.
         */
        private final Instant timestamp;

        /**
         * The raw text recorded for this log entry.
         */
        private final String text;

        /**
         * Creates a new timestamped log entry.
         *
         * @param timestamp The time the log entry was created.
         * @param text The raw log message text.
         */
        public BotLogEntry(Instant timestamp, String text) {
            this.timestamp = timestamp;
            this.text = text;
        }

        /**
         * Formats this log entry for persistent file output.
         *
         * <p>
         * If formatting fails for any reason, the exception is reported to the internal logger and {@code "null\n"}
         * is returned as a fallback line.
         * </p>
         *
         * @return The formatted log line.
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
     * Logger used for reporting internal {@code BotLogManager} errors.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The maximum number of recent log entries retained in memory.
     */
    private static final int BUFFER_CAPACITY = 100;

    /**
     * The file path used for this bot's persistent log output.
     */
    private final Path path;

    /**
     * The bounded queue containing the bot's most recent log entries.
     */
    private final Queue<BotLogEntry> buffer;

    /**
     * The bot that owns this log manager.
     */
    private final Bot bot;

    /**
     * The current live stream destination for newly recorded log entries.
     */
    private volatile BotStreamType streamType = BotStreamType.NONE;

    /**
     * Creates a new log manager for {@code bot}.
     *
     * <p>
     * Log files are stored under {@code data/game/bots/logs/} using the bot's lowercase username.
     * </p>
     *
     * @param bot The bot whose activity should be logged.
     */
    public BotLogManager(Bot bot) {
        this.bot = bot;
        path = Paths.get("data", "game", "bots", "logs",
                bot.getUsername().toLowerCase() + ".txt");
        buffer = new ConcurrentLinkedQueue<>();
    }

    /**
     * Records a new bot log message.
     * <p>
     * The message is always added to the in-memory buffer. If the buffer is already full, the oldest entry is removed
     * first. Depending on the current {@link #streamType}, the message may also be spoken by the bot and/or appended
     * asynchronously to the bot's log file.
     *
     * @param text The log message to record.
     */
    public void log(String text) {
        if (buffer.size() >= BUFFER_CAPACITY) {
            buffer.poll();
        }

        BotLogEntry entry = new BotLogEntry(Instant.now(), text);
        buffer.add(entry);

        if (streamType.isChatStream()) {
            bot.speak(text);
        }

        if (streamType.isFileStream()) {
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
     * Writes the current in-memory log buffer to this bot's log file.
     * <p>
     * This method only writes when the current stream type includes file output. The file is overwritten with the
     * current buffer contents, meaning older file entries outside the buffer are discarded.
     *
     * @return A future that completes with {@code true} if the buffer was written successfully, or {@code false} if
     * file output is disabled or the write fails.
     */
    public CompletableFuture<Boolean> writeBuffer() {
        if (streamType.isFileStream()) {
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
     * Exports the current in-memory log buffer as a single string.
     *
     * @return The formatted contents of the recent log buffer.
     */
    private String exportLogs() {
        StringJoiner sj = new StringJoiner("\n");
        for (BotLogEntry entry : buffer) {
            sj.add(entry.getFormattedMessage());
        }
        return sj.toString();
    }

    /**
     * Sets where future log messages should be streamed.
     * <p>
     * Passing {@code null} disables external streaming and falls back to {@link BotStreamType#NONE}. Existing entries
     * already stored in the in-memory buffer are not cleared.
     *
     * @param streamType The new stream destination, or {@code null} to disable external streaming.
     */
    public void setStreamType(BotStreamType streamType) {
        this.streamType = requireNonNullElse(streamType, BotStreamType.NONE);
    }
}