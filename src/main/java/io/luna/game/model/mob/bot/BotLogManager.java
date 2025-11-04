package io.luna.game.model.mob.bot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

/**
 * Manages a ring buffer of {@link BotLogEntry} types for logging and debugging purposes.
 *
 * @author lare96
 */
public final class BotLogManager {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The maximum capacity of logs.
     */
    private static final int BUFFER_CAPACITY = 50;

    /**
     * The path that logs will be streamed to.
     */
    private final Path path;

    /**
     * The buffer of logs.
     */
    private final Queue<BotLogEntry> buffer;

    /**
     * The bot.
     */
    private final Bot bot;

    /**
     * If the contents of this buffer are currently being streamed to a file.
     */
    private volatile boolean streaming;

    /**
     * Creates a new {@link BotLogManager}.
     *
     * @param bot The bot.
     */
    public BotLogManager(Bot bot) {
        this.bot = bot;
        path = Paths.get("data", "game", "bots", "logs", bot.getUsername().toLowerCase() + ".txt");
        buffer = new ArrayDeque<>(BUFFER_CAPACITY);
    }

    /**
     * Sends a new log with {@code text} to the buffer.
     *
     * @param text The log text.
     */
    public void log(String text) {
        if (buffer.size() >= BUFFER_CAPACITY) {
            buffer.poll();
        }
        BotLogEntry status = new BotLogEntry(Instant.now(), text);
        buffer.add(status);
        if (streaming) {
            bot.getService().submit(() -> {
                try {
                    Files.writeString(path, status.getFormattedMessage(), StandardOpenOption.APPEND,
                            StandardOpenOption.CREATE);
                } catch (IOException e) {
                    logger.catching(e);
                }
            });
        }
    }

    /**
     * Clears the log file for this bot, and overwrites it with the current contents of this buffer. This function will
     * immediately return {@code false} if this log manager is in streaming mode.
     *
     * @return A listenable future describing the result.
     */
    public CompletableFuture<Boolean> writeBuffer() {
        if (!streaming) {
            return bot.getService().submit(() -> {
                try {
                    Files.writeString(path, exportLogs(), StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.CREATE);
                } catch (IOException e) {
                    logger.catching(e);
                    return false;
                }
                return true;
            });
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Exports the contents of the internal buffer as a string.
     */
    private String exportLogs() {
        StringJoiner sj = new StringJoiner("\n");
        for (BotLogEntry entry : buffer) {
            sj.add(entry.getFormattedMessage());
        }
        return sj.toString();
    }

    /**
     * Sets if the contents of this buffer are currently being streamed to a file.
     */
    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }
}
