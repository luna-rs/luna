package io.luna.game.model.mob.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.luna.LunaConstants;
import io.luna.game.model.Position;
import io.luna.game.model.item.IndexedItem;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerRights;
import io.luna.game.model.mob.PlayerSettings;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.attr.AttributeKey;
import io.luna.game.model.mob.attr.AttributeValue;
import io.luna.net.codec.login.LoginResponse;
import io.luna.util.GsonUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map.Entry;

import static io.luna.util.GsonUtils.getAsType;
import static io.luna.util.GsonUtils.toJsonTree;

/**
 * A {@link PlayerSerializer} implementation that stores persistent player data in local {@code JSON} files. Is
 * also used by other serializers.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class JsonPlayerSerializer extends PlayerSerializer {

    /**
     * The path to the local files.
     */
    private static final Path DIR = Paths.get("./data/saved_players");

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
    public LoginResponse load(Player player, String enteredPassword) {
        Path filePath = computePath(player);

        // File doesn't exist, new player.
        if (!Files.exists(filePath)) {
            player.setPosition(LunaConstants.STARTING_POSITION);
            return LoginResponse.NORMAL;
        }

        try {
            // Load normally.
            return parseFromJson(player, filePath, enteredPassword);
        } catch (Exception e) {
            LOGGER.catching(e);
            return LoginResponse.COULD_NOT_COMPLETE_LOGIN;
        }
    }

    @Override
    public boolean save(Player player) {
        Path filePath = computePath(player);
        try {
            GsonUtils.writeJson(toJson(player), filePath);
        } catch (Exception e) {
            LOGGER.catching(e);
            return false;
        }
        return true;
    }

    /**
     * Load the persistent data from an {@link JsonObject}.
     *
     * @param player The player.
     * @param data The JSON object.
     * @param enteredPassword The entered password.
     * @return The login response.
     * @throws ClassNotFoundException If the attribute type is not found.
     */
    public LoginResponse fromJson(Player player, JsonObject data, String enteredPassword)
            throws ClassNotFoundException {
        String password = data.get("password").getAsString();
        if (!checkPw(enteredPassword, password)) {
            return LoginResponse.INVALID_CREDENTIALS;
        }

        Position position = getAsType(data.get("position"), Position.class);
        player.setPosition(position);

        PlayerRights rights = PlayerRights.valueOf(data.get("rights").getAsString());
        player.setRights(rights);

        int[] appearance = getAsType(data.get("appearance"), int[].class);
        player.getAppearance().setValues(appearance);

        PlayerSettings settings = getAsType(data.get("settings"), PlayerSettings.class);
        player.setSettings(settings);

        IndexedItem[] inventory = getAsType(data.get("inventory"), IndexedItem[].class);
        player.getInventory().init(inventory);

        IndexedItem[] bank = getAsType(data.get("bank"), IndexedItem[].class);
        player.getBank().init(bank);

        IndexedItem[] equipment = getAsType(data.get("equipment"), IndexedItem[].class);
        player.getEquipment().init(equipment);

        Skill[] skills = getAsType(data.get("skills"), Skill[].class);
        player.getSkills().setSkills(skills);

        long[] friends = getAsType(data.get("friends"), long[].class);
        player.setFriends(friends);

        long[] ignores = getAsType(data.get("ignores"), long[].class);
        player.setIgnores(ignores);

        JsonObject attributes = data.get("attributes").getAsJsonObject();
        for (Entry<String, JsonElement> entry : attributes.entrySet()) {
            JsonObject attr = entry.getValue().getAsJsonObject();

            Class<?> type = Class.forName(attr.get("type").getAsString());
            Object value = getAsType(attr.get("value"), type);
            player.getAttributes().get(entry.getKey()).set(value);
        }

        int[] unlockedMusic = getAsType(data.get("unlockedMusic"), int[].class);
        player.unlockedSongs = unlockedMusic;

        return LoginResponse.NORMAL;
    }

    /**
     * Parses and evaluates the {@code JSON} file at {@code path}.
     *
     * @param player The player.
     * @param path The path to the file.
     * @param enteredPassword The entered password.
     * @return The login response.
     * @throws ClassNotFoundException If the attribute type is not found.
     * @throws IOException If any I/O errors occur.
     */
    public LoginResponse parseFromJson(Player player, Path path, String enteredPassword)
            throws ClassNotFoundException, IOException {
        String jsonString = new String(Files.readAllBytes(path));
        JsonObject data = new JsonParser().parse(jsonString).getAsJsonObject();
        return fromJson(player, data, enteredPassword);
    }

    /**
     * Saves persistent data into a {@link JsonObject}.
     *
     * @param player The player.
     * @return The serialization object.
     */
    public JsonObject toJson(Player player) {
        JsonObject data = new JsonObject();
        data.addProperty("password", computePw(player));
        data.add("position", toJsonTree(player.getPosition()));
        data.addProperty("rights", player.getRights().name());
        data.add("appearance", toJsonTree(player.getAppearance().toArray()));
        data.add("settings", toJsonTree(player.getSettings()));
        data.add("inventory", toJsonTree(player.getInventory().toIndexedArray()));
        data.add("bank", toJsonTree(player.getBank().toIndexedArray()));
        data.add("equipment", toJsonTree(player.getEquipment().toIndexedArray()));
        data.add("skills", toJsonTree(player.getSkills().toArray()));
        data.add("friends", toJsonTree(player.getFriends().toArray()));
        data.add("ignores", toJsonTree(player.getIgnores().toArray()));

        JsonObject attributes = new JsonObject();
        for (Entry<String, AttributeValue> it : player.getAttributes()) {
            AttributeKey key = AttributeKey.ALIASES.get(it.getKey());
            AttributeValue value = it.getValue();

            if (key.isPersistent()) {
                JsonObject attr = new JsonObject();
                attr.addProperty("type", key.getTypeName());
                attr.add("value", toJsonTree(value.get()));

                attributes.add(key.getName(), attr);
            }
        }
        data.add("attributes", attributes);
        data.add("unlockedMusic", toJsonTree(player.unlockedSongs));
        return data;
    }

    /**
     * Computes the path to the data file of {@code player}.
     *
     * @param player The player.
     * @return The path to the file.
     */
    private Path computePath(Player player) {
        return DIR.resolve(player.getUsername() + ".json");
    }
}