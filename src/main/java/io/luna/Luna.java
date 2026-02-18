package io.luna;

import io.luna.util.GsonUtils;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.JdkLoggerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Application entrypoint and global bootstrap for the Luna RSPS runtime.
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Loading global configuration from {@code ./data/luna.json}</li>
 *   <li>Setting up logging / Netty logging integration</li>
 *   <li>Creating a {@link LunaContext} and starting {@link LunaServer}</li>
 * </ul>
 * {@link #settings()} is a globally accessible configuration handle and is initialized once in the static initializer.
 * If configuration cannot be loaded, the process fails fast with an {@link ExceptionInInitializerError}.
 *
 * @author lare96
 */
public final class Luna {

    /**
     * The asynchronous logger. Initialized after Log4j system properties are configured.
     */
    private static final Logger logger;

    /**
     * Global settings loaded from {@code ./data/luna.json}.
     */
    private static final LunaSettings settings;

    /**
     * Private constructor to prevent instantiation.
     */
    private Luna() {
    }

    static {
        try {
            Thread.currentThread().setName("InitializationThread");

            // Load settings first so logging/bootstrap can reference them if needed.
            settings = loadSettings();

            // Route Netty's internal logger through the JDK logger (commonly bridged by the hosting environment).
            InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE);

            // Configure Log4j2 before obtaining the logger instance.
            System.setProperty("log4j2.configurationFactory", "io.luna.util.logging.LoggingConfigurationFactory");
            System.setProperty("log4j.skipJansi", "true");
            System.setProperty("Log4jContextSelector",
                    "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");

            logger = LogManager.getLogger();

        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Program entrypoint. Creates the runtime {@link LunaContext} and starts the server.
     */
    public static void main(String[] args) {
        try {
            var context = new LunaContext();
            context.getServer().init();
        } catch (Exception e) {
            logger.fatal("Luna could not be started.", e);

            // Note: non-zero exit code is typically preferable for startup failure.
            System.exit(0);
        }
    }

    /**
     * Loads {@code ./data/luna.json} and parses it into a {@link LunaSettings} object.
     *
     * @return Parsed global settings.
     * @throws IOException If the file cannot be read or parsed.
     */
    private static LunaSettings loadSettings() throws IOException {
        return GsonUtils.readAsType(Paths.get("data", "luna.json"), LunaSettings.class);
    }

    /**
     * Returns global settings loaded during bootstrap.
     *
     * @return The global settings singleton.
     */
    public static LunaSettings settings() {
        return settings;
    }
}
