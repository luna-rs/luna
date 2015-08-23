package io.luna;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A class that contains the function invoked when this application is started.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class Luna {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(Luna.class);

    /**
     * A private constructor to prevent external instantiation.
     */
    private Luna() {}

    /**
     * Invoked when this program is started, initializes the service modules
     * effectively putting the server online.
     * 
     * @param args
     *            The runtime arguments, none of which are parsed.
     */
    public static void main(String[] args) {
        try {
            Server luna = new Server();
            luna.create();
        } catch (Exception e) {
            LOGGER.fatal("An error occured while initializing Luna, exiting...", e);
            System.exit(0);
        }
    }
}
