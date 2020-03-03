package io.luna.logging;

import com.moandjiezana.toml.Toml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a {@link LoggingSettings} instance from a {@code *.toml} file.
 */
public final class TomlLoggingSettingsFileReader implements LoggingSettingsFileReader {

    private Toml toml;

    public TomlLoggingSettingsFileReader(Toml toml) {
        this.toml = toml;
    }

    @Override
    public LoggingSettings read() {
        String filePath = "data/logging.toml";

        LoggingSettings settings = null;
        try (var bufferedReader = Files.newBufferedReader(Path.of(filePath))) {
            toml.read(bufferedReader);
            settings = toml.to(LoggingSettings.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return settings;
    }

}
