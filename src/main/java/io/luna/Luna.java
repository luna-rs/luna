package io.luna;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Instantiates a {@link Server} that will start this application.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Luna {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER;

    /**
     * A private constructor to discourage external instantiation.
     */
    private Luna() {
    }

    static {
        try {
            Thread.currentThread().setName("LunaInitializationThread");

            if (LunaConstants.ASYNCHRONOUS_LOGGING) {
                System.setProperty("Log4jContextSelector", // Enables asynchronous, garbage-free logging.
                    "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");
            }
            LOGGER = LogManager.getLogger();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Invoked when this program is started, initializes the {@link Server}.
     *
     * @param args The runtime arguments, none of which are parsed.
     */
    public static void main(String[] args) {
        try {
            Server luna = new Server();
            luna.init();
        } catch (Exception e) {
            LOGGER.catching(Level.FATAL, e);
            System.exit(0);
        }
    }
}
