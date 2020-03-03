package io.luna.logging;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * A collection of log4j2 settings loaded from the {@code logging.toml} file.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class LoggingSettings {

    /**
     * The root level. Determines which logging levels are displayed to the console.
     */
    private String rootLevel;

    /**
     * The format type. Determines how the logging looks in the console.
     */
    private ConsoleOutputFormat formatType;

    /**
     * The output type. Determines which stream logs will be sent to.
     */
    private ConsoleOutputLogLevel outputType;

    /**
     * The active file logs. Determines what will be logged to text files.
     */
    private Set<LoggerFileOutput> activeFileLogs;

    /**
     * @return The root level. Determines which logging levels are displayed to the console.
     */
    public String rootLevel() {
        return rootLevel;
    }

    /**
     * @return The format type. Determines how the logging looks in the console.
     */
    public ConsoleOutputFormat formatType() {
        return formatType;
    }

    /**
     * @return The output type. Determines which stream logs will be sent to.
     */
    public ConsoleOutputLogLevel outputType() {
        return outputType;
    }

    /**
     * @return The active file logs. Determines what will be logged to text files.
     */
    public ImmutableSet<LoggerFileOutput> activeFileLogs() {
        return ImmutableSet.copyOf(activeFileLogs);
    }
}
