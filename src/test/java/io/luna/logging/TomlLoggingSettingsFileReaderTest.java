package io.luna.logging;

import com.moandjiezana.toml.Toml;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TomlLoggingSettingsFileReaderTest {

    @Test
    void read() {
        TomlLoggingSettingsFileReader fileReader = new TomlLoggingSettingsFileReader(new Toml());
        LoggingSettings settings = fileReader.read();

        assertNotNull(settings);
    }

}