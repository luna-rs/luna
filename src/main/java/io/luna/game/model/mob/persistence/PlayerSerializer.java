package io.luna.game.model.mob.persistence;

import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.bot.Bot;

import java.util.Set;

/**
 * An abstraction model that handles loading and saving of data related to {@link Player} types.
 *
 * @author lare96
 */
public abstract class PlayerSerializer {

    /**
     * Loads {@link PlayerData} from an external data source.
     *
     * @param world The world context.
     * @param username The username of the player to load.
     * @return The loaded data, {@code null} if no data was found for the player.
     * @throws Exception If any errors occur.
     */
    public abstract PlayerData load(World world, String username) throws Exception;

    /**
     * Saves {@link PlayerData} to an external data source.
     *
     * @param world The world context.
     * @param username The username of the player to save
     * @param data The data to save.
     * @throws Exception If any errors occur.
     */
    public abstract void save(World world, String username, PlayerData data) throws Exception;

    /**
     * Loads the usernames of all {@link Bot} types within an external data source.
     *
     * @param world The world context.
     * @return The usernames of all bots.
     * @throws Exception If any errors occur.
     */
    public abstract Set<String> loadBotUsernames(World world) throws Exception;

    /**
     * Deletes a record matching {@code username} from an external data source.
     *
     * @param world The world context.
     * @param username The username of the record to delete.
     * @return {@code true} if the record was successfully deleted.
     * @throws Exception If any errors occurs.
     */
    public abstract boolean delete(World world, String username) throws Exception;
}