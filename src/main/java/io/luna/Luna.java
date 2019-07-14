package io.luna;

import com.moandjiezana.toml.Toml;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;

/**
 * Instantiates a {@link LunaServer} that will start Luna.
 *
 * @author lare96 <http://github.org/lare96>
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

            // Disable Jansi instantiation warning.
            System.setProperty("log4j.skipJansi", "true");

            // Enables asynchronous, garbage-free logging.
            System.setProperty("Log4jContextSelector",
                    "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");
            logger = LogManager.getLogger();
            settings = loadSettings();
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
            LunaServer luna = new LunaServer();
            luna.init();
        } catch (Exception e) {
            logger.fatal("Luna could not be started.", e);
            System.exit(0);
        }
    }

    /**
     * Loads the contents of the file and parses it into a {@link LunaSettings} object.
     *
     * @return The settings object.
     * @throws FileNotFoundException If the file wasn't found.
     */
    private static LunaSettings loadSettings() throws FileNotFoundException {
        BufferedReader reader = new BufferedReader(new FileReader("./data/luna.toml"));
        return new Toml().read(reader).to(LunaSettings.class);
    }

    /**
     * @return The global settings.
     */
    public static LunaSettings settings() {
        return settings;
    }
}
