package io.luna.game.model.mob.bot;

import com.google.common.collect.Sets;
import io.luna.game.model.World;
import io.luna.game.persistence.GameSerializer;
import io.luna.game.persistence.JsonGameSerializer;
import io.luna.game.persistence.SqlGameSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains a live and persistent registry of all {@link Bot} instances known to the server.
 * <p>
 * The {@code BotRepository} is responsible for tracking both in-memory and persistent bot data. It ensures that
 * bot usernames remain unique and that the server can distinguish bots from regular players when interacting with
 * the database or filesystem. This layer acts as the authoritative index of all bots currently active or saved to
 * persistent storage.
 * <p>
 * The repository integrates directly with the configured {@link GameSerializer}, automatically detecting whether bot
 * data is stored in JSON or SQL form. During startup, all known bot usernames are loaded into memory through
 * {@link #loadNames()} for quick lookups and name conflict prevention.
 * <p>
 * The repository maintains two primary data sets:
 * <ul>
 *     <li>{@link #savedNames} — Usernames of all persistent bots saved to disk or database.</li>
 *     <li>{@link #online} — Currently active bots, mapped by username.</li>
 * </ul>
 *
 * @author lare96
 */
public final class BotRepository {

    /**
     * The logger for this class.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * A concurrent set of all saved bot usernames.
     * <p>
     * Used to track persistent bots across server restarts and to ensure unique username generation.
     */
    private final Set<String> savedNames = Sets.newConcurrentHashSet();

    /**
     * A concurrent map of all active bots currently online, keyed by their username.
     */
    private final Map<String, Bot> online = new ConcurrentHashMap<>();

    /**
     * The world instance providing serializer and connection access.
     */
    private final World world;

    /**
     * Creates a new {@link BotRepository} bound to the specified {@link World}.
     *
     * @param world The world context.
     */
    public BotRepository(World world) {
        this.world = world;
    }

    /**
     * Loads all persistent bot usernames from the configured {@link GameSerializer}.
     * <p>
     * Depending on whether the serializer is JSON-based or SQL-based, this method will delegate to either
     * {@link #loadJsonNames()} or {@link #loadSqlNames()}.
     */
    public void loadNames() {
        try {
            GameSerializer serializer = world.getSerializerManager().getSerializer();
            if (serializer instanceof JsonGameSerializer) {
                loadJsonNames();
            } else if (serializer instanceof SqlGameSerializer) {
                loadSqlNames();
            }
        } catch (Exception e) {
            logger.error("Error while loading the usernames of saved bots.", e);
        }
    }

    /**
     * Loads all bot usernames from the JSON save directory defined by {@link JsonGameSerializer#BOT_DIR}.
     * <p>
     * Each filename in the directory corresponds to a saved bot, and the {@code .json} extension is stripped to
     * extract the username.
     *
     * @throws IOException If an I/O error occurs while reading the directory.
     */
    private void loadJsonNames() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(JsonGameSerializer.BOT_DIR)) {
            for (Path path : stream) {
                if (path.toString().endsWith(".json")) {
                    String username = path.getFileName().toString().replace(".json", "");
                    savedNames.add(username);
                }
            }
        }
    }

    /**
     * Loads all bot usernames from the SQL database by querying the {@code main_data} table for rows where
     * {@code bot = 1}.
     * <p>
     * This allows the repository to reconstruct its persistent bot index from database storage.
     */
    private void loadSqlNames() {
        try (Connection connection = world.getConnectionPool().take();
             PreparedStatement loadData = connection.prepareStatement(
                     "SELECT username FROM main_data WHERE bot = 1;")) {
            try (var results = loadData.executeQuery()) {
                while (results.next()) {
                    savedNames.add(results.getString("username"));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Persistent bot data could not be loaded.", e);
        }
    }

    /**
     * Adds a new bot to be tracked by this repository.
     * <p>
     * The bot is stored in the {@link #online} map, and if it is not marked as temporary, its username is also
     * added to {@link #savedNames}.
     *
     * @param bot The bot to track.
     */
    public void add(Bot bot) {
        online.putIfAbsent(bot.getUsername(), bot);
        if (!bot.isTemporary()) {
            savedNames.add(bot.getUsername());
        }
    }

    /**
     * Removes a bot from this repository.
     * <p>
     * Temporary bots are removed from both {@link #online} and {@link #savedNames}. Persistent bots remain in
     * {@link #savedNames}, since their data is stored permanently.
     *
     * @param bot The bot to remove.
     */
    public void remove(Bot bot) {
        online.remove(bot.getUsername());
        if (bot.isTemporary()) {
            savedNames.remove(bot.getUsername());
        }
    }

    /**
     * Checks whether a bot with the specified username is currently online.
     *
     * @param username The username to check.
     * @return {@code true} if the bot is online, otherwise {@code false}.
     */
    public boolean isOnline(String username) {
        return online.containsKey(username);
    }

    /**
     * Checks whether a bot with the specified username exists in the repository.
     * <p>
     * This returns {@code true} if the bot is either currently online or stored persistently.
     *
     * @param username The username to check.
     * @return {@code true} if the bot exists, otherwise {@code false}.
     */
    public boolean exists(String username) {
        return online.containsKey(username) || savedNames.contains(username);
    }

    /**
     * Retrieves the {@link Bot} instance associated with the given username, if it is online.
     *
     * @param username The bot username.
     * @return The {@link Bot} instance, or {@code null} if no such bot is online.
     */
    public Bot get(String username) {
        return online.get(username);
    }
}
