package io.luna;

import io.luna.game.GameService;
import io.luna.game.model.World;
import io.luna.game.plugin.PluginManager;

/**
 * An object assigned to every {@link Server} instance. It represents a single instance of the Runescape in it's entirety,
 * that being a {@link World}, {@link PluginManager}, and {@link GameService}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class LunaContext {

    /**
     * The {@link World} in this context.
     */
    private final World world = new World(this);

    /**
     * The {@link GameService} in this context.
     */
    private final GameService service = new GameService(this);

    /**
     * The {@link PluginManager} in this context.
     */
    private final PluginManager plugins = new PluginManager(this);

    /**
     * A package-private constructor to discourage external instantiation.
     */
    LunaContext() {
    }

    /**
     * @return The {@link GameService} in this context.
     */
    public GameService getService() {
        return service;
    }

    /**
     * @return The {@link World} in this context.
     */
    public World getWorld() {
        return world;
    }

    /**
     * @return The {@link PluginManager} in this context.
     */
    public PluginManager getPlugins() {
        return plugins;
    }
}
