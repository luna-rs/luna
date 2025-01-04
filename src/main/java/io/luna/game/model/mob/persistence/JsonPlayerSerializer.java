package io.luna.game.model.mob.persistence;

import io.luna.game.model.World;
import io.luna.game.model.mob.attr.Attribute;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link PlayerSerializer} implementation that stores persistent player data in local {@code JSON} files.
 *
 * @author lare96
 */
public final class JsonPlayerSerializer extends PlayerSerializer {

    /**
     * The path to the local files.
     */
    private static final Path DIR = Path.of("data", "game");

    static {
        try {
            // Initialize directory if it doesn't exist.
            if (Files.notExists(DIR)) {
                Files.createDirectories(DIR);
            }
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public PlayerData load(World world, String username) throws Exception {
        Path dir = getDir(world, username);
        Path filePath;
        if (Files.exists(dir)) {
            filePath = dir;
        } else {
            return null;
        }
        String fileContents = Files.readString(filePath);
        return Attribute.getGsonInstance().fromJson(fileContents, PlayerData.class);
    }

    @Override
    public void save(World world, String username, PlayerData data) throws Exception {
        Files.writeString(getDir(world, username), Attribute.getGsonInstance().toJson(data, PlayerData.class));
    }

    @Override
    public Set<String> loadBotUsernames(World world) throws Exception {
        Path parentDir = DIR.resolve("saved_bots");
        if(!Files.exists(parentDir)) {
            Files.createDirectory(parentDir);
            return Set.of();
        }
        try (Stream<Path> pathStream = Files.walk(parentDir)) {
            return pathStream.filter(it -> !Files.isDirectory(it)).
                    map(Path::getFileName).
                    map(Path::toString).
                    map(it -> it.replace(".json", "")).
                    collect(Collectors.toSet());
        }
    }

    @Override
    public boolean delete(World world, String username) throws Exception {
        return Files.deleteIfExists(getDir(world, username));
    }

    /**
     * Returns a direct path to this player's persistent data.
     *
     * @param username The username of the player.
     * @return The direct path.
     */
    private Path getDir(World world, String username) {
        Path parentDir = world.getBots().containsPersistent(username) ?
                DIR.resolve("saved_bots") : DIR.resolve("saved_players");
        String resolveWith = username + ".json";
        return parentDir.resolve(resolveWith);
    }
}