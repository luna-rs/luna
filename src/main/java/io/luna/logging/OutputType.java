package io.luna.logging;

/**
 * The console output type. Determines if logs will go to {@link System#out} or {@link System#err}.
 */
enum OutputType {

    /**
     * Sends all log output to {@link System#out} (white text).
     */
    OUT,

    /**
     * Sends all log output to {@link System#err} (red text).
     */
    ERR,

    /**
     * Sends TRACE -> INFO logs to {@link System#out} (white text), and WARN -> FATAL logs to {@link System#err} (red
     * text).
     */
    MIXED
}