package io.luna.game.model.mob.persistence;

import io.luna.game.model.World;
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
import java.util.HashSet;
import java.util.Set;

/**
 * A {@link PlayerSerializer} implementation that stores persistent player data in an {@code SQL} database.
 *
 * @author lare96
 */
public final class SqlPlayerSerializer extends PlayerSerializer {

    /**
     * The logger instance.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The connection pool.
     */
    private final SqlConnectionPool connectionPool = new SqlConnectionPool.Builder()
            .poolName("PlayerDataPersistence")
            .database("luna_players")
            .build();

    /**
     * Creates a new {@link SqlPlayerSerializer}.
     */
    public SqlPlayerSerializer() throws SQLException {
    }

    @Override
    public PlayerData load(World world, String username) throws Exception {
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
    public void save(World world, String username, PlayerData data) throws Exception {
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

    @Override
    public Set<String> loadBotUsernames(World world) throws Exception {
        Set<String> names = new HashSet<>();
        try (Connection connection = connectionPool.take();
             PreparedStatement loadData = connection.prepareStatement("SELECT username FROM main_data WHERE bot = 1;")) {
            try (var results = loadData.executeQuery()) {
                while (results.next()) {
                    names.add(results.getString("username"));
                }
            }
        } catch (Exception e) {
            logger.warn("Persistent bot data could not be loaded.", e);
        }
        return names;
    }

    @Override
    public boolean delete(World world, String username) throws Exception {
        try (Connection connection = connectionPool.take();
             PreparedStatement databaseId = connection.prepareStatement("SELECT id FROM main_data WHERE username = ?;");
             PreparedStatement mainData = connection.prepareStatement("DELETE FROM main_data WHERE username = ?;");
             PreparedStatement skillsData = connection.prepareStatement("DELETE FROM skills_data WHERE id = ?;")) {

            int id = -1;
            databaseId.setString(1, username);
            try (var results = databaseId.executeQuery()) {
                if (results.next()) {
                    id = results.getInt("id");
                }
            }
            if (id == -1) {
                return false;
            }

            connection.setAutoCommit(false);
            try {
                mainData.setString(1, username);
                if (mainData.executeUpdate() < 1) {
                    connection.rollback();
                    return false;
                }
                skillsData.setInt(1, id);
                if (skillsData.executeUpdate() < 1) {
                    connection.rollback();
                    return false;
                }
                connection.commit();
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            logger.warn(new ParameterizedMessage("Could not delete record for {}.", username), e);
            return false;
        }
        return true;
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
        try (var insertPlayer = connection.prepareStatement("INSERT INTO main_data (username, password, bot, rights, json_data) VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
             var insertSkills = connection.prepareStatement("INSERT INTO skills_data (id,attack_xp,attack_level,defence_xp,defence_level,strength_xp,strength_level,hitpoints_xp,hitpoints_level," +
                     "ranged_xp,ranged_level,prayer_xp,prayer_level,magic_xp,magic_level,cooking_xp,cooking_level,woodcutting_xp,woodcutting_level,fletching_xp,fletching_level,fishing_xp,fishing_level," +
                     "firemaking_xp,firemaking_level,crafting_xp,crafting_level,smithing_xp,smithing_level,mining_xp,mining_level,herblore_xp,herblore_level,agility_xp,agility_level,thieving_xp,thieving_level," +
                     "slayer_xp,slayer_level,farming_xp,farming_level,runecrafting_xp,runecrafting_level,total_level) " +
                     "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
             var updateJsonData = connection.prepareStatement("UPDATE main_data SET json_data = ? WHERE id = ?;")) {
            // Insert player data to the main table.
            insertPlayer.setString(1, username);
            insertPlayer.setString(2, data.password);
            insertPlayer.setBoolean(3, data.bot);
            insertPlayer.setString(4, data.rights.name());
            insertPlayer.setString(5, "[]");
            insertPlayer.executeUpdate();

            // Get database ID.
            int databaseId = -1;
            try (var results = insertPlayer.getGeneratedKeys()) {
                if (results.next()) {
                    databaseId = results.getInt(1);
                }
            }
            if (databaseId == -1) {
                connection.rollback();
                return;
            }

            // Insert player data to the skills table.
            insertSkills.setInt(1, databaseId);
            addSkillParameters(2, data.skills, insertSkills);
            if (insertSkills.executeUpdate() < 1) {
                connection.rollback();
                return;
            }

            // Update json data with database ID.
            data.databaseId = databaseId;
            updateJsonData.setString(1, Attribute.getGsonInstance().toJson(data));
            updateJsonData.setInt(2, databaseId);
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
        try (var updatePlayer = connection.prepareStatement("UPDATE main_data SET password = ?, rights = ?, json_data = ? WHERE id = ?;", Statement.RETURN_GENERATED_KEYS);
             var updateSkills = connection.prepareStatement("UPDATE skills_data SET attack_xp = ?,attack_level = ?,defence_xp = ?,defence_level = ?,strength_xp = ?,strength_level = ?,hitpoints_xp = ?,hitpoints_level = ?," +
                     "ranged_xp = ?,ranged_level = ?,prayer_xp = ?,prayer_level = ?,magic_xp = ?,magic_level = ?,cooking_xp = ?,cooking_level = ?,woodcutting_xp = ?,woodcutting_level = ?,fletching_xp = ?,fletching_level = ?,fishing_xp = ?,fishing_level = ?," +
                     "firemaking_xp = ?,firemaking_level = ?,crafting_xp = ?,crafting_level = ?,smithing_xp = ?,smithing_level = ?,mining_xp = ?,mining_level = ?,herblore_xp = ?,herblore_level = ?,agility_xp = ?,agility_level = ?,thieving_xp = ?,thieving_level = ?," +
                     "slayer_xp = ?,slayer_level = ?,farming_xp = ?,farming_level = ?,runecrafting_xp = ?,runecrafting_level = ?,total_level = ? WHERE id = ?;")) {

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