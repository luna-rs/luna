package io.luna.game.model.mob.bot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Queue;
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
 * entry is added. Persistent files default to the regular size limit and start over when an append would exceed it.
 * File size preferences are independent of the in-memory buffer and stream destination.
 *
 * @author lare96
 * @author TheLining
 */
public final class BotLogManager {

    /**
     * Maximum persistent log size for an individual bot. MB uses decimal units (1 MB = 1,000,000 bytes).
     */
    public enum FileSizePreference {

        /**
         * A maximum of 0.1 MB (100,000 bytes).
         */
        SMALL(100_000L),

        /**
         * A maximum of 1 MB (1,000,000 bytes), used by default.
         */
        REGULAR(1_000_000L),

        /**
         * A maximum of 10 MB (10,000,000 bytes).
         */
        LARGE(10_000_000L),

        /**
         * No application size limit. Long.MAX_VALUE is a sentinel, not a value multiplied by MB.
         */
        UNBOUNDED(Long.MAX_VALUE);

        /**
         * The byte limit, or {@link Long#MAX_VALUE} when unbounded.
         */
        private final long maxBytes;

        FileSizePreference(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        /**
         * @return The maximum file size in bytes, or {@link Long#MAX_VALUE} when unbounded.
         */
        public long getMaxBytes() {
            return maxBytes;
        }
    }

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
     * Writes this bot's persistent log output, in the order it was requested.
     */
    private final BotLogFileWriter fileWriter;

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
        Path path = Paths.get("data", "game", "bots", "logs",
                bot.getUsername().toLowerCase() + ".txt");
        fileWriter = new BotLogFileWriter(path, task -> bot.getService().submit(task));
        buffer = new ConcurrentLinkedQueue<>();
    }

    /**
     * Records a new bot log message.
     * <p>
     * The message is always added to the in-memory buffer. If the buffer is already full, the oldest entry is removed
     * first. Depending on the current {@link #streamType}, the message may also be spoken by the bot and/or appended
     * asynchronously to the bot's log file. File appends are applied in the order this method is called.
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
            fileWriter.append(entry.getFormattedMessage());
        }
    }

    /**
     * Writes the current in-memory log buffer to this bot's log file.
     * <p>
     * This method only writes when the current stream type includes file output. The file is overwritten with the
     * current buffer contents, subject to the file size preference. Older file entries outside the buffer are discarded.
     * <p>
     * The overwrite is ordered with {@link #log(String)} file appends: lines logged before this call are replaced,
     * and lines logged after it are appended to the rewritten file.
     *
     * @return A future that completes with {@code true} if the buffer was written successfully, or {@code false} if
     * file output is disabled or the write fails.
     */
    public CompletableFuture<Boolean> writeBuffer() {
        if (streamType.isFileStream()) {
            return fileWriter.overwrite(exportLogs());
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Exports the current in-memory log buffer as a single string.
     *
     * @return The formatted contents of the recent log buffer.
     */
    private String exportLogs() {
        StringBuilder sb = new StringBuilder();
        for (BotLogEntry entry : buffer) {
            sb.append(entry.getFormattedMessage());
        }
        return sb.toString();
    }

    /**
     * Changes this bot's persistent log size preference. The new limit applies to subsequent file writes,
     * including queued writes that have not started yet. An existing oversized file starts over on the next append.
     * <p>
     * Bounded files discard their old contents when full; a single oversized write is clipped on a UTF-8 boundary.
     * The in-memory buffer and stream destination are unaffected. Preferences are runtime settings and are not saved.
     *
     * @param preference The new file size preference.
     * @throws NullPointerException If {@code preference} is {@code null}.
     */
    public void setFileSizePreference(FileSizePreference preference) {
        fileWriter.setFileSizePreference(preference);
    }

    /**
     * @return This bot's current persistent log size preference, initially {@link FileSizePreference#REGULAR}.
     */
    public FileSizePreference getFileSizePreference() {
        return fileWriter.getFileSizePreference();
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
