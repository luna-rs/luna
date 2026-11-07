package io.luna.game.persistence;

import com.google.common.base.Stopwatch;
import io.luna.game.model.World;
import io.luna.game.model.mob.PlayerCredentials;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Renames saves to the canonical name of their base-37 hash (see {@link PlayerCredentials#canonicalUsername(String)}),
 * so a save made under another spelling still loads once logins use canonical names. Runs at startup, before saved bot
 * names are loaded.
 *
 * @author TheLining
 */
public final class UsernameMigration {

    // TODO Remove this file once 1.0 is released, maybe earlier if it affects startup times too much.
    private static final Stopwatch timer = Stopwatch.createUnstarted();

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Renames every save whose name isn't canonical. If a renamed save would take the name of another save, nothing is
     * renamed and startup stops, since only a person can pick which save to keep.
     *
     * @param world The world.
     * @throws IllegalStateException If two saves are one account.
     */
    public static void run(World world) {
        timer.start();
        try {
            if (world.getSerializerManager().isJson()) {
                runJson();
            } else if (world.getSerializerManager().isSql()) {
                runSql(world);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not rename saves to canonical usernames.", e);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not rename saves to canonical usernames.", e);
        }
        logger.info("Username migration completed in {}ms.", timer.elapsed(TimeUnit.MILLISECONDS));
        timer.stop();
    }

    /**
     * Renames JSON saves in place. Player and bot saves are checked together, since a name can only be one account.
     */
    private static void runJson() throws IOException {
        Map<String, Path> canonical = new HashMap<>();
        Map<String, List<Path>> renames = new HashMap<>();
        for (Path dir : List.of(JsonGameSerializer.PLAYER_DIR, JsonGameSerializer.BOT_DIR)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : stream) {
                    String fileName = file.getFileName().toString();
                    classify(fileName.substring(0, fileName.length() - ".json".length()), file, canonical, renames);
                }
            }
        }
        checkCollisions(canonical, renames);
        for (var entry : renames.entrySet()) {
            Path file = entry.getValue().get(0);
            Path renamed = file.resolveSibling(entry.getKey() + ".json");
            Files.move(file, renamed);
            logger.debug("Renamed save {} to {}.", file, renamed.getFileName());
        }
    }

    /**
     * Renames SQL saves in the {@code main} table.
     */
    private static void runSql(World world) throws SQLException {
        Map<String, String> canonical = new HashMap<>();
        Map<String, List<String>> renames = new HashMap<>();
        try (Connection connection = world.getConnectionPool().take()) {
            try (PreparedStatement select = connection.prepareStatement("SELECT username FROM main;");
                 var results = select.executeQuery()) {
                while (results.next()) {
                    String name = results.getString("username");
                    classify(name, name, canonical, renames);
                }
            }
            checkCollisions(canonical, renames);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE main SET username = ? WHERE username = ?;")) {
                for (var entry : renames.entrySet()) {
                    String name = entry.getValue().get(0);
                    update.setString(1, entry.getKey());
                    update.setString(2, name);
                    update.executeUpdate();
                    logger.debug("Renamed save {} to {}.", name, entry.getKey());
                }
            }
        }
    }

    /**
     * Records {@code save} under its canonical name. Names that can't be logged in to are skipped.
     *
     * @param name The saved name.
     * @param save The save.
     * @param canonical Saves already under their canonical name.
     * @param renames Saves to rename, by canonical name.
     */
    private static <T> void classify(String name, T save, Map<String, T> canonical, Map<String, List<T>> renames) {
        PlayerCredentials.canonicalUsername(name).ifPresent(canonicalName -> {
            if (canonicalName.equals(name)) {
                canonical.put(name, save);
            } else {
                renames.computeIfAbsent(canonicalName, key -> new ArrayList<>()).add(save);
            }
        });
    }

    /**
     * Stops startup if any canonical name would belong to more than one save.
     *
     * @param canonical Saves already under their canonical name.
     * @param renames Saves to rename, by canonical name.
     * @throws IllegalStateException If two saves are one account.
     */
    private static <T> void checkCollisions(Map<String, T> canonical, Map<String, List<T>> renames) {
        List<String> collisions = new ArrayList<>();
        renames.forEach((name, saves) -> {
            List<T> shared = new ArrayList<>(saves);
            T existing = canonical.get(name);
            if (existing != null) {
                shared.add(existing);
            }
            if (shared.size() > 1) {
                collisions.add(shared.toString());
            }
        });
        if (!collisions.isEmpty()) {
            throw new IllegalStateException("These saves are one account each: " + String.join(", ", collisions) +
                    ". Keep one save of each and restart.");
        }
    }
}
