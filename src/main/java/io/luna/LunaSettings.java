package io.luna;

import io.luna.game.model.Position;
import io.netty.util.ResourceLeakDetector.Level;

/**
 * Holds settings parsed from the {@code ./data/luna.toml} file. Effectively constants, as they are only modified by GSON.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class LunaSettings {

    private Level resourceLeakDetection;
    private int port;
    private int connectionLimit;
    private Position startingPosition;
    private double experienceMultiplier;
    private boolean pluginGui;
    private String serializer;

    /**
     * The resource leak detection level, should be {@code PARANOID} in a development environment and {@code
     * DISABLED} in a production environment. Alternatively, {@code SIMPLE} can be used fine as a compromise in both
     * scenarios.
     * <p>
     * Please note as the leak detection levels get higher, the tradeoff is a <strong>substantial</strong>
     * performance loss. {@code PARANOID} should <strong>never</strong> be used in a production environment.
     */
    public Level resourceLeakDetection() {
        return resourceLeakDetection;
    }

    /**
     * The port that the server will be bound on.
     */
    public int port() {
        return port;
    }

    /**
     * The maximum amount of connections allowed per channel. This restricts how many accounts can be logged in
     * at the same time, from the same IP address.
     */
    public int connectionLimit() {
        return connectionLimit;
    }

    /**
     * The position that new players will start on.
     */
    public Position startingPosition() {
        return startingPosition;
    }

    /**
     * The experience multiplier. This value determines how fast mobs can level up their skills.
     */
    public double experienceMultiplier() {
        return experienceMultiplier;
    }

    /**
     * If the plugin GUI should be opened on startup. The plugin GUI is an interactive interface that allows
     * for plugins to be enabled, disabled, and reloaded. If this value is false all plugins will be loaded.
     */
    public boolean pluginGui() {
        return pluginGui;
    }

    /**
     * The serializer from the {@code io.luna.game.model.mob.persistence} package that will be used to serialize and
     * deserialize player data.
     */
    public String serializer() {
        return serializer;
    }

    /**
     * To prevent public instantiation.
     */
    private LunaSettings() {

    }
}