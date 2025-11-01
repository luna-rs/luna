package io.luna.game.persistence;

import io.luna.game.model.World;
import io.luna.game.model.mob.attr.Attribute;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final Path PLAYER_DIR;

    /**
     * The path to saved bot files.
     */
    private static final Path BOT_DIR;

    static {
        try {
            // Initialize directory if it doesn't exist.
            DIR = Path.of("data", "game");
            Files.createDirectories(DIR);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
        PLAYER_DIR = DIR.resolve("saved_players");
        BOT_DIR = DIR.resolve("bots").resolve("saved_bots");

    }

    @Override
    public PlayerData loadPlayer(World world, String username) {
        Path parentDir = getParentDir(world, username);
        Path dir = parentDir.resolve(username + ".json");
        if (!Files.exists(dir)) {
            return null;
        }
        try {
            return Attribute.getGsonInstance().fromJson(Files.readString(dir), parentDir == PLAYER_DIR ?
                    PlayerData.class : BotData.class);
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

    @Override
    public Set<String> loadBotUsernames(World world) {
        if (!Files.exists(BOT_DIR)) {
            try {
                Files.createDirectory(BOT_DIR);
                return Set.of();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        try (Stream<Path> pathStream = Files.walk(BOT_DIR)) {
            return pathStream.filter(it -> !Files.isDirectory(it)).
                    map(Path::getFileName).
                    map(Path::toString).
                    map(it -> it.replace(".json", "")).
                    collect(Collectors.toSet());
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
        return world.getBots().containsPersistent(username) ? BOT_DIR : PLAYER_DIR;
    }
}