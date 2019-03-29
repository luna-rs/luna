package io.luna;

import com.google.gson.JsonObject;
import com.moandjiezana.toml.Toml;
import io.luna.game.model.Position;
import io.luna.game.model.chunk.ChunkMobComparator;
import io.netty.util.ResourceLeakDetector.Level;

import java.io.File;
import java.util.HashSet;
import java.util.TreeSet;

import static io.luna.util.GsonUtils.getAsType;

/**
 * Holds constants parsed from the {@code luna.toml} file.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class LunaConstants {

    static {
        try {
            Toml tomlReader = new Toml().read(new File("./data/luna.toml"));

            JsonObject networkConstants = tomlReader.getTable("network").to(JsonObject.class);
            PORT = networkConstants.get("port").getAsInt();
            RESOURCE_LEAK_DETECTION = Level.valueOf(networkConstants.get("resource_leak_detection").getAsString());
            CONNECTION_LIMIT = networkConstants.get("connection_threshold").getAsInt();

            JsonObject gameConstants = tomlReader.getTable("game").to(JsonObject.class);
            STARTING_POSITION = getAsType(gameConstants.get("starting_position"), Position.class);
            EXPERIENCE_MULTIPLIER = gameConstants.get("experience_multiplier").getAsDouble();
            SERIALIZER = gameConstants.get("player_serializer").getAsString();

            JsonObject utilityConstants = tomlReader.getTable("utility").to(JsonObject.class);
            PLUGIN_GUI = utilityConstants.get("plugin_gui").getAsBoolean();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * A private constructor.
     */
    private LunaConstants() {}

    /**
     * The resource leak detection level, should be {@code PARANOID} in a development environment and {@code
     * DISABLED} in a production environment. Alternatively, {@code SIMPLE} can be used fine as a compromise in both
     * scenarios.
     * <p>
     * Please note as the leak detection levels get higher, the tradeoff is a <strong>substantial</strong>
     * performance loss. {@code PARANOID} should <strong>never</strong> be used in a production environment.
     */
    public static final Level RESOURCE_LEAK_DETECTION;

    /**
     * The port that the server will be bound on.
     */
    public static final int PORT;

    /**
     * The maximum amount of connections allowed per channel. This restricts how many accounts can be logged in
     * at the same time, from the same IP address.
     */
    public static final int CONNECTION_LIMIT;

    /**
     * The position that new players will start on.
     */
    public static final Position STARTING_POSITION;

    /**
     * The experience multiplier. This value determines how fast mobs can level up their skills.
     */
    public static final double EXPERIENCE_MULTIPLIER;

    /**
     * If the plugin GUI should be opened on startup. The plugin GUI is an interactive interface that allows
     * for plugins to be enabled, disabled, and reloaded. If this value is false all plugins will be loaded.
     */
    public static final boolean PLUGIN_GUI;

    /**
     * The serializer from the {@code io.luna.game.model.mob.persistence} package that will be used to serialize and
     * deserialize player data.
     */
    public static final String SERIALIZER;
}
