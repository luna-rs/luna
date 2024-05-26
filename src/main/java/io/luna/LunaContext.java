package io.luna;

import io.luna.game.cache.Cache;
import io.luna.game.model.World;
import io.luna.game.plugin.PluginManager;
import io.luna.game.service.GameService;

/**
 * A model representing a single instance of Runescape. Only one instance should exist at a time.
 *
 * @author lare96
 */
public final class LunaContext {

    /**
     * The cache resource.
     */
    private final Cache cache = new Cache();

    /**
     * The server.
     */
    private final LunaServer server = new LunaServer(this);

    /**
     * The world.
     */
    private final World world = new World(this);

    /**
     * The game service.
     */
    private final GameService game = new GameService(this);

    /**
     * The plugin manager.
     */
    private final PluginManager plugins = new PluginManager(this);

    /**
     * A package-private constructor.
     */
    LunaContext() {
    }

    /**
     * @return The cache resource.
     */
    public Cache getCache() {
        return cache;
    }

    /**
     * @return The world.
     */
    public World getWorld() {
        return world;
    }

    /**
     * @return The game service.
     */
    public GameService getGame() {
        return game;
    }

    /**
     * @return The plugin manager.
     */
    public PluginManager getPlugins() {
        return plugins;
    }

    /**
     * @return The server.
     */
    public LunaServer getServer() {
        return server;
    }
}
