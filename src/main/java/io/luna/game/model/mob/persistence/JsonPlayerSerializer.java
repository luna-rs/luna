package io.luna.game.model.mob.persistence;

import io.luna.LunaContext;
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

    /**
     * Creates a new {@link JsonPlayerSerializer}.
     *
     * @param context The context.
     */
    public JsonPlayerSerializer(LunaContext context) {
        super(context);
    }

    @Override
    public PlayerData load(String username) throws Exception {
        Path dir = getDir(username);
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
    public void save(String username, PlayerData data) throws Exception {
        Files.writeString(getDir(username), Attribute.getGsonInstance().toJson(data, PlayerData.class));
    }

    @Override
    public Set<String> loadBotUsernames() throws Exception {
        Path parentDir = DIR.resolve("saved_bots");
        try (Stream<Path> pathStream = Files.walk(parentDir)) {
            return pathStream.
                    map(Path::getFileName).
                    map(Path::toString).
                    map(it -> it.replace(".json", "")).
                    collect(Collectors.toSet());
        }
    }

    @Override
    public boolean delete(String username) throws Exception {
        return Files.deleteIfExists(getDir(username));
    }

    /**
     * Returns a direct path to this player's persistent data.
     *
     * @param username The username of the player.
     * @return The direct path.
     */
    private Path getDir(String username) {
        Path parentDir = context.getWorld().getBots().containsPersistent(username) ?
                DIR.resolve("saved_bots") : DIR.resolve("saved_players");
        String resolveWith = username + ".json";
        return parentDir.resolve(resolveWith);
    }
}