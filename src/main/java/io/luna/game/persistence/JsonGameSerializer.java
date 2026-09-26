package io.luna.game.persistence;

import io.luna.game.model.World;
import io.luna.game.model.mob.attr.Attribute;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A {@link GameSerializer} implementation that stores persistent player data in local {@code JSON} files.
 *
 * @author lare96
 */
public final class JsonGameSerializer extends GameSerializer {

    /**
     * The parent path to the local files.
     */
    private static final Path DIR;

    /**
     * The path to saved player files.
     */
    public static final Path PLAYER_DIR;

    /**
     * The path to saved bot files.
     */
    public static final Path BOT_DIR;

    static {
        try {
            // TODO Bot leveling test fix: create both save folders, or every save fails on a fresh checkout. This is a
            //  test to see how it works; we should go further and add a test for it.
            DIR = Path.of("data", "game");
            PLAYER_DIR = DIR.resolve("saved_players");
            BOT_DIR = DIR.resolve("bots").resolve("saved_bots");
            Files.createDirectories(PLAYER_DIR);
            Files.createDirectories(BOT_DIR);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public PlayerData loadPlayer(World world, String username) {
        Path parentDir = getParentDir(world, username);
        Path dir = parentDir.resolve(username + ".json");
        if (!Files.exists(dir)) {
            return null;
        }
        try {
            return Attribute.getGsonInstance().fromJson(Files.readString(dir), PlayerData.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void savePlayer(World world, String username, PlayerData data) {
        try {
            Files.writeString(getDir(world, username), Attribute.getGsonInstance().toJson(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean deletePlayer(World world, String username) {
        try {
            return Files.deleteIfExists(getDir(world, username));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a direct path to this player's persistent data file.
     *
     * @param username The username of the player.
     * @return The direct path.
     */
    private Path getDir(World world, String username) {
        Path parentDir = getParentDir(world, username);
        return parentDir.resolve(username + ".json");
    }

    /**
     * Returns a direct path to the folder of persistent data.
     *
     * @param username The username of the player.
     * @return The direct path.
     */
    private Path getParentDir(World world, String username) {
        return world.getBots().exists(username) ? BOT_DIR : PLAYER_DIR;
    }
}