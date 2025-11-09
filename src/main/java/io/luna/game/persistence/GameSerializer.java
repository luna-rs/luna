package io.luna.game.persistence;

import io.luna.game.model.World;
import io.luna.game.model.mob.Player;

/**
 * An abstraction model that allows for interfacing between in-memory {@link Player} based models and external
 * data sources such as text files, JSON files, and SQL databases. Functions are usually applied within
 * {@link PersistenceService}.
 *
 * @author lare96
 */
public abstract class GameSerializer {

    /**
     * Loads {@link PlayerData} from an external data source.
     *
     * @param world The world context.
     * @param username The username of the player to load.
     * @return The loaded data, {@code null} if no data was found for the player.
     */
    public abstract PlayerData loadPlayer(World world, String username);

    /**
     * Saves {@link PlayerData} to an external data source.
     *
     * @param world The world context.
     * @param username The username of the player to save
     * @param data The data to save.
     */
    public abstract void savePlayer(World world, String username, PlayerData data);

    /**
     * Deletes a record matching {@code username} from an external data source.
     *
     * @param world The world context.
     * @param username The username of the record to delete.
     * @return {@code true} if the record was successfully deleted.
     */
    public abstract boolean deletePlayer(World world, String username);

}