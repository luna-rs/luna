package io.luna.util.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Holds settings parsed from the "logging" section in {@code ./data/luna.json} file.
 *
 * @author lare96
 */
public final class LoggingSettings {

    /**
     * The logging format type. Determines how the logging looks in the console.
     */
    public enum FormatType {

        /**
         * Basic formatting. Only takes up one line and displays a small amount of info (date, time, logging level).
         */
        BASIC,

        /**
         * Verbose formatting. Takes up two lines and displays data useful for debugging (date, time, logger name,
         * current thread, logging level).
         */
        VERBOSE
    }

    /**
     * The console output type. Determines if logs will go to {@link System#out} or {@link System#err}.
     */
    public enum OutputType {

        /**
         * Sends all log output to {@link System#out} (white text).
         */
        OUT,

        /**
         * Sends all log output to {@link System#err} (red text).
         */
        ERR,

        /**
         * Sends TRACE -> INFO logs to {@link System#out} (white text), and WARN -> FATAL logs to {@link System#err}
         * (red text).
         */
        MIXED
    }

    /**
     * The file output type. Holds data for file loggers.
     */
    public enum FileOutputType {

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
         * Creates a new {@link FileOutputType}.
         */
        FileOutputType(String loggerName) {
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

    /**
     * The root level. Determines which logging levels are displayed to the console.
     */
    private final String rootLevel;

    /**
     * The format type. Determines how the logging looks in the console.
     */
    private final FormatType formatType;

    /**
     * The output type. Determines which stream logs will be sent to.
     */
    private final OutputType outputType;

    /**
     * The active file logs. Determines what will be logged to text files.
     */
    private final Set<FileOutputType> activeFileLogs;

    /**
     * @return The root level. Determines which logging levels are displayed to the console.
     */
    public String rootLevel() {
        return rootLevel;
    }

    /**
     * @return The format type. Determines how the logging looks in the console.
     */
    public FormatType formatType() {
        return formatType;
    }

    /**
     * @return The output type. Determines which stream logs will be sent to.
     */
    public OutputType outputType() {
        return outputType;
    }

    /**
     * @return The unmodifiable set of active file logs. Determines what will be logged to text files.
     */
    public Set<FileOutputType> activeFileLogs() {
        if(activeFileLogs == null || activeFileLogs.isEmpty()) {
            return Collections.unmodifiableSet(EnumSet.allOf(FileOutputType.class));
        }
        return Collections.unmodifiableSet(activeFileLogs);
    }

    // Never called.
    private LoggingSettings(String rootLevel, FormatType formatType, OutputType outputType, Set<FileOutputType> activeFileLogs) {
        this.rootLevel = rootLevel;
        this.formatType = formatType;
        this.outputType = outputType;
        this.activeFileLogs = activeFileLogs;
    }
}
