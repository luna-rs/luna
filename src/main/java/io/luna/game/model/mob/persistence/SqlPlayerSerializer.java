package io.luna.game.model.mob.persistence;

import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.attr.Attribute;
import io.luna.util.SqlConnectionPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A {@link PlayerSerializer} implementation that stores persistent player data in an {@code SQL} database.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class SqlPlayerSerializer extends PlayerSerializer {

    /**
     * The logger instance.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The connection pool.
     */
    private final SqlConnectionPool connectionPool;

    /**
     * Creates a new {@link SqlPlayerSerializer}.
     */
    public SqlPlayerSerializer() throws SQLException {
        connectionPool = new SqlConnectionPool.Builder()
                .poolName("PlayerDataPersistence")
                .database("luna_players")
                .build();
    }

    @Override
    public PlayerData load(String username) throws Exception {
        PlayerData data = null;
        try (var connection = connectionPool.take();
             var loadData = connection.prepareStatement("SELECT json_data FROM main_data WHERE username = ?;")) {
            loadData.setString(1, username);

            try (var results = loadData.executeQuery()) {
                if (results.next()) {
                    String jsonData = results.getString("json_data");
                    data = Attribute.getGsonInstance().fromJson(jsonData, PlayerData.class);
                }
            }
        } catch (Exception e) {
            logger.warn(new ParameterizedMessage("{}'s data could not be loaded.", username), e);
        }
        return data;
    }

    @Override
    public void save(String username, PlayerData data) throws Exception {
        try (var connection = connectionPool.take()) {
            connection.setAutoCommit(false);
            try {
                if (data.databaseId == -1) {
                    saveNewPlayer(connection, username, data);
                } else {
                    saveExistingPlayer(connection, data);
                }
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            logger.warn(new ParameterizedMessage("{}'s data could not be saved.", username), e);
        }
    }

    /**
     * Saves a new player to the database.
     *
     * @param connection The connection.
     * @param username The username.
     * @param data The player's data.
     * @throws SQLException If any errors occur.
     */
    private void saveNewPlayer(Connection connection, String username, PlayerData data) throws SQLException {
        try (var insertPlayer = connection.prepareStatement("INSERT INTO main_data (username, password, rights, json_data) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
             var insertSkills = connection.prepareStatement("INSERT INTO skills_data (player_id,attack_xp,attack_level,defence_xp,defence_level,strength_xp,strength_level,hitpoints_xp,hitpoints_level," +
                     "ranged_xp,ranged_level,prayer_xp,prayer_level,magic_xp,magic_level,cooking_xp,cooking_level,woodcutting_xp,woodcutting_level,fletching_xp,fletching_level,fishing_xp,fishing_level," +
                     "firemaking_xp,firemaking_level,crafting_xp,crafting_level,smithing_xp,smithing_level,mining_xp,mining_level,herblore_xp,herblore_level,agility_xp,agility_level,thieving_xp,thieving_level," +
                     "slayer_xp,slayer_level,farming_xp,farming_level,runecrafting_xp,runecrafting_level,total_level) " +
                     "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
             var updateJsonData = connection.prepareStatement("UPDATE main_data SET json_data = ? WHERE player_id = ?;")) {

            // Insert player data to the main table.
            insertPlayer.setString(1, username);
            insertPlayer.setString(2, data.password);
            insertPlayer.setString(3, data.rights.name());
            insertPlayer.setString(4, "");
            insertPlayer.executeUpdate();

            // Get database ID.
            int playerId = -1;
            try (var results = insertPlayer.getGeneratedKeys()) {
                if (results.next()) {
                    playerId = results.getInt(1);
                }
            }
            if (playerId == -1) {
                connection.rollback();
                return;
            }

            // Insert player data to the skills table.
            insertSkills.setInt(1, playerId);
            addSkillParameters(2, data.skills, insertSkills);
            if (insertSkills.executeUpdate() < 1) {
                connection.rollback();
                return;
            }

            // Update json data with database ID.
            data.databaseId = playerId;
            updateJsonData.setString(1, Attribute.getGsonInstance().toJson(data));
            updateJsonData.setInt(2, playerId);
            if (updateJsonData.executeUpdate() < 1) {
                connection.rollback();
                return;
            }

            // Commit transaction.
            connection.commit();
        } catch (Exception e) {
            connection.rollback();
            throw new IllegalStateException(e);
        }
    }

    /**
     * Saves an existing player to the database.
     *
     * @param connection The connection.
     * @param data The player's data.
     * @throws SQLException If any errors occur.
     */
    private void saveExistingPlayer(Connection connection, PlayerData data) throws SQLException {
        try (var updatePlayer = connection.prepareStatement("UPDATE main_data SET password = ?, rights = ?, json_data = ? WHERE player_id = ?;", Statement.RETURN_GENERATED_KEYS);
             var updateSkills = connection.prepareStatement("UPDATE skills_data SET attack_xp = ?,attack_level = ?,defence_xp = ?,defence_level = ?,strength_xp = ?,strength_level = ?,hitpoints_xp = ?,hitpoints_level = ?," +
                     "ranged_xp = ?,ranged_level = ?,prayer_xp = ?,prayer_level = ?,magic_xp = ?,magic_level = ?,cooking_xp = ?,cooking_level = ?,woodcutting_xp = ?,woodcutting_level = ?,fletching_xp = ?,fletching_level = ?,fishing_xp = ?,fishing_level = ?," +
                     "firemaking_xp = ?,firemaking_level = ?,crafting_xp = ?,crafting_level = ?,smithing_xp = ?,smithing_level = ?,mining_xp = ?,mining_level = ?,herblore_xp = ?,herblore_level = ?,agility_xp = ?,agility_level = ?,thieving_xp = ?,thieving_level = ?," +
                     "slayer_xp = ?,slayer_level = ?,farming_xp = ?,farming_level = ?,runecrafting_xp = ?,runecrafting_level = ?,total_level = ? WHERE player_id = ?;")) {

            // Update player data in the main table.
            updatePlayer.setString(1, data.password);
            updatePlayer.setString(2, data.rights.name());
            updatePlayer.setString(3, Attribute.getGsonInstance().toJson(data));
            updatePlayer.setInt(4, data.databaseId);
            if (updatePlayer.executeUpdate() < 1) {
                connection.rollback();
                return;
            }

            // Update player data in the skills table.
            int index = addSkillParameters(1, data.skills, updateSkills);
            updateSkills.setInt(index, data.databaseId);
            if (updateSkills.executeUpdate() < 1) {
                connection.rollback();
                return;
            }

            // Commit transaction.
            connection.commit();
        } catch (Exception e) {
            connection.rollback();
            throw new IllegalStateException(e);
        }
    }

    /**
     * Prepares parameters for the SQL statements that loop through skills.
     *
     * @param index The starting index.
     * @param skills The skills.
     * @param statement The SQL statement instance.
     * @return The new index.
     * @throws SQLException If any errors occur.
     */
    private int addSkillParameters(int index, Skill[] skills, PreparedStatement statement) throws SQLException {
        int totalLevel = 0;
        for (var skill : skills) {
            int level = skill.getStaticLevel();
            statement.setDouble(index++, skill.getExperience());
            statement.setInt(index++, level);
            totalLevel += level;
        }
        statement.setInt(index++, totalLevel);
        return index;
    }
}