package io.luna.game.model.mob.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.luna.LunaConstants;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.login.LoginResponse;
import io.luna.util.GsonUtils;
import io.luna.util.SqlConnectionPool;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.sql.Statement;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link PlayerSerializer} implementation that stores persistent player data in an {@code SQL} database.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class SqlPlayerSerializer extends PlayerSerializer {

    // TODO This is still experimental and hasn't been tested.
    // TODO Saving json text is temporary and will be removed in the future.

    /**
     * The connection pool.
     */
    private final SqlConnectionPool connectionPool;

    /**
     * The serializer for JSON data.
     */
    private final JsonPlayerSerializer json = new JsonPlayerSerializer();

    /**
     * Creates a new {@link SqlPlayerSerializer}.
     */
    public SqlPlayerSerializer() throws SQLException {
        connectionPool = new SqlConnectionPool.Builder()
                .poolName("PersistenceSqlPool")
                .database("players")
                .timeout(7)
                .build();
    }

    @Override
    public LoginResponse load(Player player, String enteredPassword) {
        try {
            // Connect to database.
            try (Connection connection = connectionPool.take()) {
                return sqlLoad(player, connection.createStatement(), enteredPassword);
            }
        } catch (Exception e) {
            // Exception while loading data, abort login.
            LOGGER.warn(player + " data could not be loaded.", e);
            return LoginResponse.COULD_NOT_COMPLETE_LOGIN;
        }
    }

    @Override
    public boolean save(Player player) {
        World world = player.getWorld();
        try {
            // Save to database.
            try (Connection connection = connectionPool.take()) {
                sqlSave(player, connection.createStatement());
            }
        } catch (SQLTransientException e) {
            // Transient exception, we can retry it.
            LOGGER.warn("Data for " + player + " could not be saved, retrying...", e);
            world.savePlayer(player);
            return false;
        } catch (SQLException e) {
            // Normal exception, cannot be retried.
            throw new RuntimeException(e);
        } catch (Exception e) {
            // handle later TODO
        }
        return true;
    }

    /**
     * Loads {@code player} from the backing database.
     *
     * @param player The player.
     * @param statement The SQL statement.
     * @param enteredPassword The entered password.
     * @throws SQLException If any SQl errors occur.
     */
    private LoginResponse sqlLoad(Player player, Statement statement, String enteredPassword)
            throws SQLException, ClassNotFoundException {

        // Lookup database id.
        ResultSet playerId = statement.executeQuery("SELECT player_id " +
                "FROM players " +
                "WHERE name = '" + player.getUsername() + "';");
        if (!playerId.next()) {
            // They don't have one, so they're a new player.
            player.setPosition(LunaConstants.STARTING_POSITION);
            return LoginResponse.NORMAL;
        }
        long databaseId = playerId.getLong("player_id");
        player.setDatabaseId(databaseId);

        // Set JSON data.
        ResultSet data = statement.executeQuery("SELECT data " +
                "FROM players " +
                "WHERE player_id = " + player.getDatabaseId() + ";");
        checkState(data.next(), "There was no result for 'data' in the database.");
        JsonObject jsonData = new JsonParser().parse(data.getString("data")).getAsJsonObject();
        return json.fromJson(player, jsonData, enteredPassword);
    }

    /**
     * Saves {@code player} to the backing database.
     *
     * @param player The player.
     * @param statement The SQL statement.
     * @throws SQLException If any SQl errors occur.
     */
    private void sqlSave(Player player, Statement statement) throws Exception {
        String jsonString = GsonUtils.GSON.toJson(json.toJson(player));

        if (player.getDatabaseId() == -1) {
            statement.executeUpdate("INSERT INTO players(name, data) " +
                    "VALUES('" + player.getUsername() + "', '" + jsonString + "');");
        } else {
            statement.executeUpdate("UPDATE players " +
                    "SET data = '" + jsonString + "' " +
                    "WHERE player_id = " + player.getDatabaseId() + ";");
        }
    }
}