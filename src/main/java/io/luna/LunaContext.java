package io.luna;

import io.luna.game.GameService;
import io.luna.game.cache.Cache;
import io.luna.game.model.World;
import io.luna.game.plugin.PluginManager;

/**
 * Root runtime container for a single Luna server instance.
 * <p>
 * {@link LunaContext} owns core subsystems that are expected to be singletons for the lifetime of the process:
 * <ul>
 *   <li>{@link Cache} (377 cache resource + decoders)</li>
 *   <li>{@link World} (game state, mobs, tasks, services)</li>
 *   <li>{@link GameService} (core game loop / tick processing)</li>
 *   <li>{@link PluginManager} (scripts/plugins, event listeners, hot reload wiring)</li>
 *   <li>{@link LunaServer} (startup orchestration + network boot)</li>
 * </ul>
 * This object is passed around as a dependency hub to prevent “global static everything” while still keeping
 * initialization explicit and deterministic.
 *
 * @author lare96
 */
public final class LunaContext {

    /**
     * Cache resource for the #377 cache.
     */
    private final Cache cache = new Cache();

    /**
     * Server bootstrap/orchestration for this context.
     */
    private final LunaServer server = new LunaServer(this);

    /**
     * Global world state.
     */
    private final World world = new World(this);

    /**
     * Core game loop service.
     */
    private final GameService game = new GameService(this);

    /**
     * Plugin/script manager.
     */
    private final PluginManager plugins = new PluginManager(this);

    /**
     * Package-private constructor. Contexts are intended to be created only by {@link Luna}.
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
     * @return The game service (tick loop).
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
     * @return The server bootstrap/orchestrator.
     */
    public LunaServer getServer() {
        return server;
    }
}
