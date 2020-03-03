package io.luna.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The file output type. Holds data for file loggers.
 */
public enum LoggerFileOutput {

    /**
     * Log all {@link System#out} data to a text file.
     */
    CONSOLE_OUT("root"),

    /**
     * Log all {@link System#err} data to a text file.
     */
    CONSOLE_ERR("root"),

    /**
     * Log all player public chat to a text file.
     */
    CHAT("ChatLogger"),

    /**
     * Log all player commands to a text file.
     */
    COMMANDS("CommandsLogger"),

    /**
     * Log all instances of player's dropping items to a text file.
     */
    ITEM_DROP("ItemDropLogger"),

    /**
     * Log all instances of player's picking up items to a text file.
     */
    ITEM_PICKUP("ItemPickupLogger"),

    /**
     * Log all private messages to a text file.
     */
    PRIVATE_MESSAGE("PrivateMessageLogger");

    /**
     * The logger's name.
     */
    private final String loggerName;

    /**
     * Creates a new {@link LoggerFileOutput}.
     */
    LoggerFileOutput(String loggerName) {
        this.loggerName = loggerName;
    }

    /**
     * @return The logger's name.
     */
    public String getLoggerName() {
        return loggerName;
    }

    /**
     * Returns the file name, which is always the lowercase version of {@link #name()}.
     */
    public String getFileName() {
        return name().toLowerCase();
    }

    /**
     * Returns the logger instance for this file output entry.
     */
    public Logger getLogger() {
        return LogManager.getLogger(loggerName);
    }

    /**
     * Returns the logging level for this file output entry.
     */
    public Level getLevel() {
        return Level.getLevel(name());
    }
}