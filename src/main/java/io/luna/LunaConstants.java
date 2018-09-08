package io.luna;

import com.google.gson.JsonObject;
import com.moandjiezana.toml.Toml;
import io.luna.game.model.Position;
import io.luna.game.model.region.RegionUpdateComparator;
import io.netty.util.ResourceLeakDetector.Level;

import java.io.File;
import java.math.BigInteger;
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
            RSA_MODULUS = new BigInteger(networkConstants.get("rsa_modulus").getAsString());
            RSA_EXPONENT = new BigInteger(networkConstants.get("rsa_exponent").getAsString());
            RESOURCE_LEAK_DETECTION = Level.valueOf(networkConstants.get("resource_leak_detection").getAsString());
            CONNECTION_LIMIT = networkConstants.get("connection_threshold").getAsInt();

            JsonObject gameConstants = tomlReader.getTable("game").to(JsonObject.class);
            STAGGERED_UPDATING = gameConstants.get("staggered_updating").getAsBoolean();
            STARTING_POSITION = getAsType(gameConstants.get("starting_position"), Position.class);
            EXPERIENCE_MULTIPLIER = gameConstants.get("experience_multiplier").getAsDouble();
            PACKET_126_CACHING = gameConstants.get("packet_126_caching").getAsBoolean();
            PASSWORD_HASHING = gameConstants.get("password_hashing").getAsBoolean();

            JsonObject utilityConstants = tomlReader.getTable("utility").to(JsonObject.class);
            ASYNCHRONOUS_LOGGING = utilityConstants.get("asynchronous_logging").getAsBoolean();
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
     * The public RSA modulus value.
     */
    public static final BigInteger RSA_MODULUS;

    /**
     * The private RSA exponent value.
     */
    public static final BigInteger RSA_EXPONENT;

    /**
     * The maximum amount of connections allowed per channel. This restricts how many accounts can be logged in
     * at the same time, from the same IP address.
     */
    public static final int CONNECTION_LIMIT;

    /**
     * If staggered updating should be enabled. This feature is disabled by default because it introduces a slight
     * performance regression. It should only be enabled by highly populated servers.
     * <p>
     * Only {@code 15} players and npcs are updated for a player per cycle regardless of how many are technically
     * present around them. A mob that hasn't yet been updated for a player won't even be visible. This can pose
     * potential problems if a large volume of players are in one place.
     * <p>
     * A solution to this problem is <strong>staggered updating</strong>, which in this context means to update the
     * most important players first. This is done through the {@link RegionUpdateComparator}.
     * <p>
     * The tradeoff for this feature is a slight performance regression, as surrounding mobs have to be stored
     * within a {@link TreeSet} (O(log n) performance) instead of a regular {@link HashSet} (O(1) performance).
     */
    public static final boolean STAGGERED_UPDATING;

    /**
     * The position that new players will start on.
     */
    public static final Position STARTING_POSITION;

    /**
     * The experience multiplier. This value determines how fast mobs can level up their skills.
     */
    public static final double EXPERIENCE_MULTIPLIER;

    /**
     * If asynchronous and garbage-free logging should be enabled. This feature improves logging performance.
     */
    public static final boolean ASYNCHRONOUS_LOGGING;

    /**
     * If the String values sent within packet #126 should be recorded and cached. This should be enabled if
     * you're performing frame #126 writes very frequently. The caching will improve performance if many writes
     * are done, but can slightly regress performance if too few are done.
     */
    public static final boolean PACKET_126_CACHING;

    /**
     * If the plugin GUI should be opened on startup. The plugin GUI is an interactive interface that allows
     * for plugins to be enabled, disabled, and reloaded. If this value is false all plugins will be loaded.
     */
    public static final boolean PLUGIN_GUI;

    /**
     * If passwords should be hashed using {@code BCrypt} to help protect against player database leaks and hacks.
     * {@code BCrypt} is a one-way hash meaning that no one can decrypt it to obtain the original passwords. This
     * should always be enabled when in a production environment for security reasons.
     */
    public static final boolean PASSWORD_HASHING;
}
