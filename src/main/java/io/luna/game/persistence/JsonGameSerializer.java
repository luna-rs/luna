package io.luna.game.persistence;

import io.luna.game.model.World;
import io.luna.game.model.mob.attr.Attribute;
import io.luna.game.model.mob.bot.BotSchedule;
import io.luna.util.GsonUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
    public PlayerData loadPlayer(World world, String username) throws Exception {
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
    public void savePlayer(World world, String username, PlayerData data) throws Exception {
        Files.writeString(getDir(world, username), Attribute.getGsonInstance().toJson(data, PlayerData.class));
    }

    @Override
    public boolean deletePlayer(World world, String username) throws Exception {
        return Files.deleteIfExists(getDir(world, username));
    }

    @Override
    public Set<String> loadBotUsernames(World world) throws Exception {
        Path parentDir = DIR.resolve("bots").resolve("saved_bots");
        if (!Files.exists(parentDir)) {
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
    public Map<String, BotSchedule> synchronizeBotSchedules(World world) throws Exception {
        Path parentPath = DIR.resolve("bots").resolve("sessions");
        Map<String, BotSchedule> scheduleMap = new HashMap<>();
        try (Stream<Path> pathStream = Files.walk(parentPath)) { // Cache existing schedules.
            for (Iterator<Path> it = pathStream.iterator(); it.hasNext(); ) {
                BotSchedule loadSchedule = GsonUtils.readAsType(it.next(), BotSchedule.class);
                scheduleMap.put(loadSchedule.getUsername(), loadSchedule);
            }
        }

        for (Iterator<String> it = world.getBots().persistentIterator(); it.hasNext(); ) { // Build new ones where needed.
            String username = it.next();
            if(scheduleMap.containsKey(username)) {
                continue;
            }
            Path schedulePath = parentPath.resolve(username + ".json");
            BotSchedule newSchedule = BotSchedule.createRandomSchedule(username);

            GsonUtils.writeJson(GsonUtils.toJsonTree(newSchedule), schedulePath);
            scheduleMap.put(username, newSchedule);
        }
        return scheduleMap;
    }

    // todo test bot schedule serialization for json
    @Override
    public boolean saveBotSchedule(World world, BotSchedule schedule) throws Exception {
        Path parentDir = DIR.resolve("bots").resolve("sessions");
        Files.writeString(parentDir.resolve(schedule.getUsername() + ".json"),
                Attribute.getGsonInstance().toJson(schedule, BotSchedule.class));
        return true;
    }

    /**
     * Returns a direct path to this player's persistent data.
     *
     * @param username The username of the player.
     * @return The direct path.
     */
    private Path getDir(World world, String username) {
        Path parentDir = world.getBots().containsPersistent(username) ?
                DIR.resolve("bots").resolve("saved_bots") : DIR.resolve("saved_players");
        String resolveWith = username + ".json";
        return parentDir.resolve(resolveWith);
    }
}