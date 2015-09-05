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
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(Luna.class);

    /**
     * A private constructor to discourage external instantiation.
     */
    private Luna() {}

    /**
     * Invoked when this program is started, initializes the {@link Server}.
     * 
     * @param args
     *            The runtime arguments, none of which are parsed.
     */
    public static void main(String[] args) {
        try {
            Server luna = new Server();
            luna.create();
        } catch (Exception e) {
            LOGGER.catching(Level.FATAL, e);
            System.exit(0);
        }
    }
}
