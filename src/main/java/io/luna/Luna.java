package io.luna;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.script.ScriptException;

/**
 * Instantiates a {@link LunaServer} that will start Luna.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Luna  {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER;

    /**
     * A private constructor.
     */
    private Luna() {
    }

    static {
        try {
            Thread.currentThread().setName("LunaInitializationThread");

            if (LunaConstants.ASYNCHRONOUS_LOGGING) {
                // Enables asynchronous, garbage-free logging.
                System.setProperty("Log4jContextSelector",
                    "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");
            }
            LOGGER = LogManager.getLogger();
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
            LOGGER.fatal("Luna could not be started.", e);
            System.exit(0);
        }
    }
}
