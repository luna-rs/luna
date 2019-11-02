package io.luna.game.model.mob.persistence;

/**
 * An abstraction model that handles loading and saving of {@link PlayerData}.
 *
 * @author lare96 <http://github.com/lare96>
 */
public abstract class PlayerSerializer {

    /**
     * Loads {@link PlayerData} from an external data source.
     *
     * @param username The username of the player to load.
     * @return The loaded data, {@code null} if no data was found for the player.
     * @throws Exception If any errors occur.
     */
    public abstract PlayerData load(String username) throws Exception;

    /**
     * Saves {@link PlayerData} to an external data source.
     *
     * @param username The username of the player to save
     * @param data The data to save.
     * @throws Exception If any errors occur.
     */
    public abstract void save(String username, PlayerData data) throws Exception;
}