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
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import static io.luna.util.GsonUtils.getAsType;
import static io.luna.util.GsonUtils.toJsonTree;

/**
 * A model containing functions that allow for synchronous and asynchronous player serialization.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerSerializer {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The path to serialized player files.
     */
    private static final Path FILE_DIR = Paths.get("./data/saved_players");

    /**
     * The player being serialized.
     */
    private final Player player;

    /**
     * The path to the serialized file.
     */
    private final Path path;

    /**
     * Creates a new {@link PlayerSerializer}.
     *
     * @param player The player being serialized.
     */
    public PlayerSerializer(Player player) {
        this.player = player;
        path = FILE_DIR.resolve(player.getUsername() + ".json");
    }

    static { /* Initialize serialization directory. */
        try {
            if (Files.notExists(FILE_DIR)) {
                Files.createDirectory(FILE_DIR);
            }
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Serializes all persistent data.
     */
    public void save() {

        /* Cache all main token tables. */
        JsonObject tokens = new JsonObject();
        tokens.addProperty("password", player.getPassword());
        tokens.add("position", toJsonTree(player.getPosition()));
        tokens.addProperty("rights", player.getRights().name());
        tokens.addProperty("running", player.getWalkingQueue().isRunning());
        tokens.add("appearance", toJsonTree(player.getAppearance().toArray()));
        tokens.add("inventory", toJsonTree(player.getInventory().toIndexedArray()));
        tokens.add("bank", toJsonTree(player.getBank().toIndexedArray()));
        tokens.add("equipment", toJsonTree(player.getEquipment().toIndexedArray()));
        tokens.add("skills", toJsonTree(player.getSkills().toArray()));

        /* Cache all attribute tokens. */
        JsonObject attributeTokens = new JsonObject();
        for (Entry<String, AttributeValue<?>> it : player.getAttributes()) {
            AttributeKey<?> key = AttributeKey.ALIASES.get(it.getKey());
            AttributeValue<?> value = it.getValue();

            if (key.isPersistent()) {
                JsonObject attributeElementTokens = new JsonObject();
                attributeElementTokens.addProperty("type", key.getTypeName());
                attributeElementTokens.add("value", toJsonTree(value.get()));

                attributeTokens.add(key.getName(), attributeElementTokens);
            }
        }

        /* Serialize all tokens. */
        tokens.add("attributes", attributeTokens);
        try {
            GsonUtils.writeJson(tokens, path.toFile());
        } catch (Exception e) {
            LOGGER.catching(e);
        }
    }

    /**
     * Asynchronously serializes all persistent data.
     */
    public ListenableFuture<Void> asyncSave(GameService service) {
        return service.submit((Callable<Void>) () -> {
            save();
            return null;
        });
    }

    /**
     * Deserializes all persistent data and verifies the password.
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

            Position position = getAsType(jsonReader.get("position"), Position.class);
            player.setPosition(position);

            PlayerRights rights = PlayerRights.valueOf(jsonReader.get("rights").getAsString());
            player.setRights(rights);

            boolean running = jsonReader.get("running").getAsBoolean();
            player.getWalkingQueue().setRunning(running);

            int[] appearance = getAsType(jsonReader.get("appearance"), int[].class);
            player.getAppearance().setValues(appearance);

            IndexedItem[] inventory = getAsType(jsonReader.get("inventory"), IndexedItem[].class);
            player.getInventory().setItems(inventory);

            IndexedItem[] bank = getAsType(jsonReader.get("bank"), IndexedItem[].class);
            player.getBank().setItems(bank);

            IndexedItem[] equipment = getAsType(jsonReader.get("equipment"), IndexedItem[].class);
            player.getEquipment().setItems(equipment);

            Skill[] skills = getAsType(jsonReader.get("skills"), Skill[].class);
            player.getSkills().setSkills(skills);

            JsonObject attr = jsonReader.get("attributes").getAsJsonObject();
            for (Entry<String, JsonElement> it : attr.entrySet()) {
                JsonObject attrReader = it.getValue().getAsJsonObject();

                Class<?> type = Class.forName(attrReader.get("type").getAsString());
                Object data = getAsType(attrReader.get("value"), type);
                player.getAttributes().get(it.getKey()).set(data);
            }
        } catch (Exception e) {
            LOGGER.catching(e);
            return LoginResponse.COULD_NOT_COMPLETE_LOGIN;
        }
        return LoginResponse.NORMAL;
    }
}
