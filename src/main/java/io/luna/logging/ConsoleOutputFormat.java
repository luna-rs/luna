package io.luna.logging;

/**
 * The logging format type. Determines how the logging looks in the console.
 */
enum ConsoleOutputFormat {

    /**
     * Basic formatting. Only takes up one line and displays a small amount of info (date, time, logging level).
     */
    BASIC,

    /**
     * Verbose formatting. Takes up two lines and displays data useful for debugging (date, time, logger name, current
     * thread, logging level).
     */
    VERBOSE
}