package io.luna.game.model.mob.persistence;

import io.luna.game.model.mob.attr.Attribute;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A {@link PlayerSerializer} implementation that stores persistent player data in local {@code JSON} files.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class JsonPlayerSerializer extends PlayerSerializer {

    /**
     * The path to the local files.
     */
    private static final Path DIR = Path.of("data", "saved_players");

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
    public PlayerData load(String username) throws Exception {
        var filePath = DIR.resolve(username + ".json");
        if (!Files.exists(filePath)) {
            return null;
        }
        String fileContents = Files.readString(filePath);
        return Attribute.getGsonInstance().fromJson(fileContents, PlayerData.class);
    }

    @Override
    public void save(String username, PlayerData data) throws Exception {
        Files.writeString(DIR.resolve(username + ".json"), Attribute.getGsonInstance().toJson(data, PlayerData.class));
    }
}