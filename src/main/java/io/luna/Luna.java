package io.luna;

import com.moandjiezana.toml.Toml;
import io.luna.util.LoggingSettings;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.JdkLoggerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Instantiates a {@link LunaServer} that will start Luna.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Luna {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER;

    /**
     * The global settings.
     */
    private static final LunaSettings LUNA_SETTINGS;

    /**
     * The log4j2 settings.
     */
    private static final LoggingSettings LOGGING_SETTINGS;

    /**
     * The {@link Toml} instance used to read {@code .toml} files.
     */
    private static final Toml TOML = new Toml();

    static {
        try {
            Thread.currentThread().setName("InitializationThread");

            InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE);
            LOGGING_SETTINGS = loadLoggingSettings();
            System.setProperty("log4j2.configurationFactory", "io.luna.util.LoggingConfigurationFactory");
            System.setProperty("log4j.skipJansi", "true");
            System.setProperty("Log4jContextSelector",
                    "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");
            LOGGER = LogManager.getLogger();
            LUNA_SETTINGS = loadSettings();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * A private constructor.
     */
    private Luna() {
        throw new UnsupportedOperationException("This class cannot be instantiated!");
    }

    /**
     * Invoked when this program is started, initializes Luna.
     *
     * @param args The program arguments, always ignored.
     */
    public static void main(String[] args) {
        try {
            new LunaServer().init();
        } catch (IOException e) {
            LOGGER.fatal("Luna could not be started.", e);
            System.exit(0);
        }
    }

    /**
     * Loads the contents of the file and parses it into a {@link LunaSettings} object.
     *
     * @return The settings object.
     */
    private static LunaSettings loadSettings() throws IOException {
        try (var bufferedReader = Files.newBufferedReader(Path.of("data", "luna.toml"))) {
            return TOML.read(bufferedReader).to(LunaSettings.class);
        }
    }

    /**
     * Loads the contents of the file and parses it into a {@link LoggingSettings} object.
     *
     * @return The logging settings object.
     */
    private static LoggingSettings loadLoggingSettings() throws IOException {
        try (var bufferedReader = Files.newBufferedReader(Path.of("data", "logging.toml"))) {
            return TOML.read(bufferedReader).to(LoggingSettings.class);
        }
    }

    /**
     * @return The global settings.
     */
    public static LunaSettings settings() {
        return LUNA_SETTINGS;
    }

    /**
     * @return The logging settings.
     */
    public static LoggingSettings loggingSettings() {
        return LOGGING_SETTINGS;
    }
}
