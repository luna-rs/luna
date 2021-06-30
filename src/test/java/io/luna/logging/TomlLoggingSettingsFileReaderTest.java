package io.luna.logging;

import com.moandjiezana.toml.Toml;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TomlLoggingSettingsFileReaderTest {

    @Test
    void read() throws IOException {
        LoggingSettings settings;
        Toml toml = new Toml();
        try (var bufferedReader = Files.newBufferedReader(Path.of("data", "logging.toml"))) {
            settings = toml.read(bufferedReader).to(LoggingSettings.class);
        }

        assertNotNull(settings);
    }

}