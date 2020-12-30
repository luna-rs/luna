package io.luna.game.model.mob.persistence;

import io.luna.Luna;
import io.luna.LunaSettings;
import io.luna.game.model.mob.Player;
import io.luna.util.ReflectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mindrot.jbcrypt.BCrypt;

/**
 * A model responsible for creating the serializer and performing synchronous loads and saves.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PlayerPersistence {

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The current serializer.
     */
    private final PlayerSerializer serializer;

    /**
     * Creates a new {@link PlayerPersistence}.
     *
     * @throws ClassCastException If the serializer could not be created.
     */
    public PlayerPersistence() throws ClassCastException {
        serializer = computeSerializer();
    }

    /**
     * Shortcut to {@link #save(String, PlayerData)}.
     */
    public void save(Player player) throws Exception {
        save(player.getUsername(), player.getSaveData());
    }

    /**
     * Synchronously saves persistent data.
     *
     * @param username The username of the player to save.
     * @param data The data to save.
     */
    public void save(String username, PlayerData data) throws Exception {
        if (data.needsHash) {
            data.password = BCrypt.hashpw(data.plainTextPassword, BCrypt.gensalt(Luna.settings().passwordStrength()));
        }
        serializer.save(username, data);
    }

    /**
     * Synchronously loads persistent data for {@code username}.
     *
     * @param username The username of the player to load.
     * @return The loaded data.
     */
    public PlayerData load(String username) throws Exception {
        return serializer.load(username);
    }

    /**
     * Initializes a new serializer based on data within {@code luna.toml}.
     *
     * @return The serializer.
     * @throws ClassCastException If the serializer could not be created.
     */
    private PlayerSerializer computeSerializer() throws ClassCastException {
        String name = Luna.settings().serializer();
        try {
            String fullName = "io.luna.game.model.mob.persistence." + name;
            return ReflectionUtils.newInstanceOf(fullName);
        } catch (ClassCastException e) {
            logger.fatal(name + " not an instance of PlayerSerializer.");
            throw e;
        }
    }
}