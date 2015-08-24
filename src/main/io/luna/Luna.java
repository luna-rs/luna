package io.luna;

import io.luna.game.GameService;
import io.luna.game.model.World;
import io.luna.game.plugin.PluginManager;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A class that manages global state and contains the function invoked when this
 * application is started.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class Luna {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(Luna.class);

    /**
     * The game service that will run game logic.
     */
    private static GameService service = new GameService();

    /**
     * The world that will manage {@link io.luna.game.model.Entity} types.
     */
    private static World world = new World();

    /**
     * The plugin manager that will manage {@code Scala} plugins.
     */
    private static PluginManager plugins = new PluginManager();

    /**
     * A private constructor to discourage external instantiation.
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
            LOGGER.catching(Level.FATAL, e);
            System.exit(0);
        }
    }

    /**
     * Gets the game service that runs game logic.
     * 
     * @return The game service.
     */
    public static GameService getService() {
        return service;
    }

    /**
     * Gets the world that will manage {@code Entity} types.
     * 
     * @return The world that manages entities.
     */
    public static World getWorld() {
        return world;
    }

    /**
     * Gets the plugin manager for our {@code Scala} plugins.
     * 
     * @return The plugin manager.
     */
    public static PluginManager getPlugins() {
        return plugins;
    }
}
