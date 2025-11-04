package io.luna.game.model.mob.bot;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Represents a single log entry from a {@link Bot}.
 *
 * @author lare96
 */
public class BotLogEntry {

    /**
     * The formatter.
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("MMM d, hh:mm:s a").
            withZone(ZoneId.of("UTC"));

    /**
     * The timestamp.
     */
    private final Instant timestamp;

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
    public BotLogEntry(Instant timestamp, String text) {
        this.timestamp = timestamp;
        this.text = text;
    }

    /**
     * @return The formatted message that will be presented.
     */
    public String getFormattedMessage() {
        try {
            return "[" + FORMATTER.format(timestamp) + "] " + text + "\n";
        } catch (Exception e) {
            e.printStackTrace();
            return "null\n";
        }
    }

    /**
     * @return The timestamp.
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * @return The log message.
     */
    public String getText() {
        return text;
    }
}
