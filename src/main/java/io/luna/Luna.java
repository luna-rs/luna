package io.luna;

import io.luna.util.GsonUtils;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.JdkLoggerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Instantiates a {@link LunaServer} that will start Luna.
 *
 * @author lare96
 */
public final class Luna {

    /**
     * The asynchronous logger.
     */
    private static final Logger logger;

    /**
     * The global settings.
     */
    private static final LunaSettings settings;

    /**
     * A private constructor.
     */
    private Luna() {
    }

    static {
        try {
            Thread.currentThread().setName("InitializationThread");

            settings = loadSettings();

            InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE);
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
     * Invoked when this program is started, initializes Luna.
     *
     * @param args The program arguments, always ignored.
     */
    public static void main(String[] args) {
        try {
            var context = new LunaContext();
            context.getServer().init();
        } catch (Exception e) {
            logger.fatal("Luna could not be started.", e);
            System.exit(0);
        }
    }

    /**
     * Loads the contents of the file and parses it into a {@link LunaSettings} object.
     *
     * @return The settings object.
     */
    private static LunaSettings loadSettings() throws IOException {
        return GsonUtils.readAsType(Paths.get("data", "luna.json"), LunaSettings.class);
    }

    /**
     * @return The global settings.
     */
    public static LunaSettings settings() {
        return settings;
    }
}
