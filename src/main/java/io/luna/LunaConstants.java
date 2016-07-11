package io.luna;

import com.google.gson.JsonObject;
import com.moandjiezana.toml.Toml;
import io.luna.game.model.region.RegionPriorityComparator;
import io.netty.util.ResourceLeakDetector.Level;

import java.io.File;
import java.math.BigInteger;

/**
 * A set of constants parsed from the {@code luna.toml} file.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class LunaConstants {

    static {
        try {
            JsonObject reader = new Toml().read(new File("./data/luna.toml")).
                getTable("settings").
                to(JsonObject.class);

            PORT = reader.get("port").getAsInt();
            RSA_MODULUS = new BigInteger(reader.get("rsa_modulus").getAsString());
            RSA_EXPONENT = new BigInteger(reader.get("rsa_exponent").getAsString());
            RESOURCE_LEAK_DETECTION = Level.valueOf(reader.get("resource_leak_detection_level").getAsString());
            CONNECTION_LIMIT = reader.get("connection_threshold").getAsInt();
            STAGGERED_UPDATING = reader.get("staggered_updating").getAsBoolean();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * The resource leak detection level, should be {@code PARANOID} in a development environment and {@code DISABLED} in a
     * production environment. Alternatively, {@code SIMPLE} can be used fine as a compromise in both scenarios.
     * <p>
     * Please note as the leak detection levels get higher, the tradeoff is a <b>substantial</b> performance loss. {@code
     * PARANOID} should <b>never</b> be used in a production environment.
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
     * The maximum amount of connections allowed per channel.
     */
    public static final int CONNECTION_LIMIT;

    /**
     * If staggered updating should be enabled.
     * <p>
     * As you know, only 15 players and npcs are updated for you per cycle regardless of how many are technically present
     * around you. If there are more than 15 players your region and a mob that hasn't been updated for you decides to attack
     * you for example, you won't even be able to see the mob attacking you until it's eventually updated! Staggered updating
     * solves this and many other issues by updating the most important mobs before anyone else.
     * <p>
     * The price of this feature is performance, as all of the mobs in your region have to be sorted by the {@link
     * RegionPriorityComparator} to determine which are the most important. Very minimal servers have this feature available,
     * simply because most servers aren't active enough to run into these types of issues. Therefore, I've kept the option
     * available for bigger servers that might need this feature and can deal with the performance loss while smaller servers
     * can disable it completely.
     * <p>
     * To summarize, very large servers should have this enabled, any other servers should have it disabled.
     */
    public static final boolean STAGGERED_UPDATING;
}
