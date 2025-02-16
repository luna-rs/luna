package io.luna.game.model.mob.persistence;

import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.BotSchedule;

import java.util.Map;
import java.util.Set;

/**
 * An abstraction model that handles loading and saving of data related to {@link Player} types.
 *
 * @author lare96
 */
public abstract class PlayerSerializer {
 // todo rename, more of general purpose game serializer
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
     * Deletes a record matching {@code username} from an external data source.
     *
     * @param world The world context.
     * @param username The username of the record to delete.
     * @return {@code true} if the record was successfully deleted.
     * @throws Exception If any errors occurs.
     */
    public abstract boolean delete(World world, String username) throws Exception;

    /**
     * Loads the usernames of all {@link Bot} types from an external data source.
     *
     * @param world The world context.
     * @return The usernames of all bots.
     * @throws Exception If any errors occur.
     */
    public abstract Set<String> loadBotUsernames(World world) throws Exception;

    /**
     * Creates new {@link BotSchedule} types where needed, and returns all existing schedules from the external data
     * source.
     *
     * @param world The world context.
     * @return All existing sessions sorted into a map.
     * @throws Exception If any errors occur.
     */
    public abstract Map<String, BotSchedule> synchronizeBotSchedules(World world) throws Exception;

    /**
     * Saves a single {@link BotSchedule} to an external data source.
     *
     * @param world The world context.
     * @param schedule The schedule.
     * @return {@code true} if successful.
     * @throws Exception If any errors occur.
     */
    public abstract boolean saveBotSchedule(World world, BotSchedule schedule) throws Exception;
}