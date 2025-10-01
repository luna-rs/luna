package io.luna.game.model.mob.bot;

import java.text.SimpleDateFormat;
import java.time.Instant;

/**
 * Represents a single log entry from a {@link Bot}.
 *
 * @author lare96
 */
public class BotLogEntry {

    /**
     * The formatter.
     */
    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("MMM d | hh:mm aaa");

    /**
     * The timestamp.
     */
    private final long timestamp;

    /**
     * The log message.
     */
    private final String text;

    /**
     * Creates a new {@link BotLogEntry}
     *
     * @param timestamp The timestamp.
     * @param text The log message.
     */
    public BotLogEntry(long timestamp, String text) {
        this.timestamp = timestamp;
        this.text = text;
    }

    /**
     * @return The formatted message that will be presented.
     */
    public String getFormattedMessage() {
        return "[" + FORMATTER.format(Instant.ofEpochMilli(timestamp) + "] " + text);
    }

    /**
     * @return The timestamp.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @return The log message.
     */
    public String getText() {
        return text;
    }
}
