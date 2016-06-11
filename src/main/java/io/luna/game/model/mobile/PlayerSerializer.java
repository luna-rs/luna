package io.luna.game.model.mobile;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.luna.game.GameService;
import io.luna.game.model.Position;
import io.luna.game.model.item.IndexedItem;
import io.luna.game.model.mobile.attr.AttributeKey;
import io.luna.game.model.mobile.attr.AttributeValue;
import io.luna.net.codec.login.LoginResponse;
import io.luna.util.GsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

/**
 * Functions that allow for synchronous and asynchronous serialization and deserialization of a {@link Player}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerSerializer {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The {@link Path} to all of the serialized {@link Player} data.
     */
    private static final Path FILE_DIR = Paths.get("./data/saved_players");

    /**
     * The {@link Player} being serialized or deserialized.
     */
    private final Player player;

    /**
     * The {@link Path} to the character file.
     */
    private final Path path;

    /**
     * Creates a new {@link PlayerSerializer}.
     *
     * @param player The {@link Player} being serialized or deserialized.
     */
    public PlayerSerializer(Player player) {
        this.player = player;
        path = FILE_DIR.resolve(player.getUsername() + ".json");
    }

    static {
        if (Files.notExists(FILE_DIR)) {
            try {
                Files.createDirectory(FILE_DIR);
            } catch (Exception e) {
                LOGGER.catching(e);
            }
        }
    }

    /**
     * Attempts to serialize all persistent data for the {@link Player}.
     */
    public void save() {
        Map<String, Object> mainTable = new LinkedHashMap<>();

        mainTable.put("password", player.getPassword());
        mainTable.put("position", player.getPosition());
        mainTable.put("rights", player.getRights().name());
        mainTable.put("running", player.getWalkingQueue().isRunning());
        mainTable.put("appearance", player.getAppearance().toArray());
        mainTable.put("inventory", player.getInventory().toIndexedArray());
        mainTable.put("bank", player.getBank().toIndexedArray());
        mainTable.put("equipment", player.getEquipment().toIndexedArray());
        mainTable.put("skills", player.getSkills().toArray());

        Map<String, Object> attrTable = new HashMap<>();
        for (Entry<String, AttributeValue<?>> it : player.attributes) {
            AttributeKey<?> key = AttributeKey.ALIASES.get(it.getKey());
            AttributeValue<?> value = it.getValue();

            if (key.isPersistent()) {
                Map<String, Object> attrSubTable = new LinkedHashMap<>();
                attrSubTable.put("type", key.getTypeName());
                attrSubTable.put("value", value.get());

                attrTable.put(key.getName(), attrSubTable);
            }
        }
        mainTable.put("attributes", attrTable);

        try (Writer writer = new FileWriter(path.toFile())) {
            writer.write(GsonUtils.GSON.toJson(mainTable));
        } catch (IOException e) {
            LOGGER.catching(e);
        }
    }

    /**
     * Attempts to asynchronously serialize all persistent data for the {@link Player}. Returns a {@link ListenableFuture}
     * detailing the progress and result of the asynchronous task.
     *
     * @param service The {@link GameService} to use for asynchronous execution.
     * @return The {@link ListenableFuture} detailing progress and the result.
     */
    public ListenableFuture<Void> asyncSave(GameService service) {
        return service.submit((Callable<Void>) () -> {
            save();
            return null;
        });
    }

    /**
     * Attempts to deserialize all persistent data for the {@link Player}.
     *
     * @param expectedPassword The expected password to be compared against the deserialized password.
     * @return The {@link LoginResponse} determined by the deserialization.
     */
    public LoginResponse load(String expectedPassword) {
        if (!Files.exists(path)) {
            return LoginResponse.NORMAL;
        }
        try (Reader reader = new FileReader(path.toFile())) {
            JsonObject jsonReader = (JsonObject) new JsonParser().parse(reader);

            String password = jsonReader.get("password").getAsString();
            if (!expectedPassword.equals(password)) {
                return LoginResponse.INVALID_CREDENTIALS;
            }

            Position position = GsonUtils.getAsType(jsonReader.get("position"), Position.class);
            player.setPosition(position);

            PlayerRights rights = PlayerRights.valueOf(jsonReader.get("rights").getAsString());
            player.setRights(rights);

            boolean running = jsonReader.get("running").getAsBoolean();
            player.getWalkingQueue().setRunning(running);

            int[] appearance = GsonUtils.getAsType(jsonReader.get("appearance"), int[].class);
            player.getAppearance().setValues(appearance);

            IndexedItem[] inventory = GsonUtils.getAsType(jsonReader.get("inventory"), IndexedItem[].class);
            player.getInventory().setIndexedItems(inventory);

            IndexedItem[] bank = GsonUtils.getAsType(jsonReader.get("bank"), IndexedItem[].class);
            player.getBank().setIndexedItems(bank);

            IndexedItem[] equipment = GsonUtils.getAsType(jsonReader.get("equipment"), IndexedItem[].class);
            player.getEquipment().setIndexedItems(equipment);

            Skill[] skills = GsonUtils.getAsType(jsonReader.get("skills"), Skill[].class);
            player.getSkills().setSkills(skills);

            JsonObject attr = jsonReader.get("attributes").getAsJsonObject();
            for (Entry<String, JsonElement> it : attr.entrySet()) {
                JsonObject attrReader = it.getValue().getAsJsonObject();

                Class<?> type = Class.forName(attrReader.get("type").getAsString());
                Object data = GsonUtils.getAsType(attrReader.get("value"), type);

                player.attr().get(it.getKey()).set(data);
            }
        } catch (Exception e) {
            LOGGER.catching(e);
            return LoginResponse.COULD_NOT_COMPLETE_LOGIN;
        }
        return LoginResponse.NORMAL;
    }

    /**
     * Attempts to asynchronously deserialize all persistent data for the {@link Player}. Returns a {@link ListenableFuture}
     * detailing the progress and result of the asynchronous task.
     *
     * @param expectedPassword The expected password to be compared against the deserialized password.
     * @param service The {@link GameService} to use for asynchronous execution.
     * @return The {@link ListenableFuture} detailing progress and the result.
     */
    public ListenableFuture<LoginResponse> asyncLoad(String expectedPassword, GameService service) {
        return service.submit(() -> load(expectedPassword));
    }
}
