package io.luna.game.model.mob.persistence;

import io.luna.LunaConstants;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.login.LoginResponse;
import io.luna.util.ReflectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A model responsible for creating the serializer, and performing synchronous loads and saves.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PlayerPersistence {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

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
     * Saves persistent data for {@code player}.
     *
     * @param player The player.
     * @return {@code true} if the save completed successfully.
     */
    public boolean save(Player player) {
        try {
            return serializer.save(player);
        } catch (Exception e) {
            LOGGER.catching(e);
            return false;
        }
    }

    /**
     * Loads persistent data for {@code player} and returns a {@link LoginResponse}.
     *
     * @param player The player.
     * @return The login response.
     */
    public LoginResponse load(Player player) {
        try {
            return serializer.load(player, player.getPassword());
        } catch (Exception e) {
            LOGGER.catching(e);
            return LoginResponse.COULD_NOT_COMPLETE_LOGIN;
        }
    }

    /**
     * Initializes a new serializer based on data within {@code luna.toml}.
     *
     * @return The serializer.
     * @throws ClassCastException If the serializer could not be created.
     */
    private PlayerSerializer computeSerializer() throws ClassCastException {
        String name = LunaConstants.SERIALIZER;
        try {
            String fullName = "io.luna.game.model.mob.persistence." + name;
            return ReflectionUtils.newInstanceOf(fullName);
        } catch (ClassCastException e) {
            LOGGER.fatal(name + " not an instance of PlayerSerializer.");
            throw e;
        }
    }
}