package io.luna.game.model;

import io.luna.game.GameService;

/**
 * Manages global state as well as the various types in the
 * {@code io.luna.game.model} package and subpackages.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class World {

    /**
     * The main game logic service.
     */
    private static GameService service = new GameService();

    /**
     * A private constructor to prevent external instantiation.
     */
    private World() {}

    /**
     * Gets the main game logic service.
     * 
     * @return the logic service.
     */
    public static GameService getService() {
        return service;
    }
}
