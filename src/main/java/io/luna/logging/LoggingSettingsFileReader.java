package io.luna.logging;

/**
 * Responsible for reading {@link LoggingSettings} from a file.
 */
public interface LoggingSettingsFileReader {

    LoggingSettings read();
}
