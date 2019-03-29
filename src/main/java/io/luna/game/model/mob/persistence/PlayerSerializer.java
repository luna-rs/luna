package io.luna.game.model.mob.persistence;

import io.luna.LunaConstants;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.login.LoginResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mindrot.jbcrypt.BCrypt;

/**
 * An abstraction model that handles loading and saving of persistent player data.
 *
 * @author lare96 <http://github.com/lare96>
 */
public abstract class PlayerSerializer {

    /**
     * The asynchronous logger.
     */
    static final Logger LOGGER = LogManager.getLogger();

    /**
     * Loads persistent player data, and returns a login response based on that data.
     *
     * @param player The player to load.
     * @param enteredPassword The password to verify.
     * @return The login response.
     */
    public abstract LoginResponse load(Player player, String enteredPassword);

    /**
     * Saves persistent player data.
     *
     * @param player The player to save.
     * @return {@code true} if the data was successfully saved.
     */
    public abstract boolean save(Player player);

    /**
     * Checks the input password for equality with the saved password. Used when loading data.
     *
     * @param enteredPassword The password sent from the client.
     * @param savedPassword The saved password.
     * @return {@code true} if the passwords are equal.
     */
    final boolean checkPw(String enteredPassword, String savedPassword) {
        return BCrypt.checkpw(enteredPassword, savedPassword);
    }

    /**
     * Returns either a hashed or plaintext password. Used when saving data.
     *
     * @return The password.
     */
    final String computePw(Player player) {
        return BCrypt.hashpw(player.getPassword(), BCrypt.gensalt(12));
    }
}