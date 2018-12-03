package io.luna.game.model.mob;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.luna.LunaConstants;
import io.luna.game.model.Position;
import io.luna.game.model.item.IndexedItem;
import io.luna.game.model.mob.attr.AttributeKey;
import io.luna.game.model.mob.attr.AttributeValue;
import io.luna.net.codec.login.LoginResponse;
import io.luna.util.GsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mindrot.jbcrypt.BCrypt;

import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map.Entry;

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

    static {
        try {
            // Initialize directories, if they don't exist.
            if (Files.notExists(FILE_DIR)) {
                Files.createDirectories(FILE_DIR);
            }
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Returns either a hashed or plaintext password.
     *
     * @return The password. Might be hashed.
     */
    private String getPassword() {
        String pw = player.getPassword();
        return LunaConstants.PASSWORD_HASHING ? BCrypt.hashpw(pw, BCrypt.gensalt()) : pw;
    }

    /**
     * Checks the input password for equality with the saved password.
     *
     * @param inputPassword The password sent from the client.
     * @param savedPassword The saved character file password.
     * @return {@code true} if the passwords are equal.
     */
    private boolean checkPassword(String inputPassword, String savedPassword) {
        if (LunaConstants.PASSWORD_HASHING) {
            return BCrypt.checkpw(inputPassword, savedPassword);
        }
        return inputPassword.equals(savedPassword);
    }


    /**
     * Serializes all persistent data.
     */
    public void save() {
        JsonObject tokens = new JsonObject();

        // Save all main tokens vital to the player.
        tokens.addProperty("password", getPassword());
        tokens.add("position", toJsonTree(player.getPosition()));
        tokens.addProperty("rights", player.getRights().name());
        tokens.addProperty("running", player.getWalking().isRunning());
        tokens.add("appearance", toJsonTree(player.getAppearance().toArray()));
        tokens.add("inventory", toJsonTree(player.getInventory().toIndexedArray()));
        tokens.add("bank", toJsonTree(player.getBank().toIndexedArray()));
        tokens.add("equipment", toJsonTree(player.getEquipment().toIndexedArray()));
        tokens.add("skills", toJsonTree(player.getSkills().toArray()));
        tokens.add("friends", toJsonTree(player.getFriends().toArray()));
        tokens.add("ignores", toJsonTree(player.getIgnores().toArray()));

        // Save all player attributes.
        JsonObject attributeTokens = new JsonObject();
        for (Entry<String, AttributeValue> it : player.getAttributes()) {
            AttributeKey key = AttributeKey.ALIASES.get(it.getKey());
            AttributeValue value = it.getValue();

            // Save only if the attribute is persistent.
            if (key.isPersistent()) {
                JsonObject struct = new JsonObject();
                struct.addProperty("type", key.getTypeName());
                struct.add("value", toJsonTree(value.get()));

                attributeTokens.add(key.getName(), struct);
            }
        }
        tokens.add("attributes", attributeTokens);

        // Write the tokens to the character file.
        try {
            GsonUtils.writeJson(tokens, path.toFile());
        } catch (Exception e) {
            LOGGER.catching(e);
        }
    }

    /**
     * Asynchronously serializes all persistent data.
     *
     * @return The listenable future.
     */
    public ListenableFuture<?> asyncSave() {
        return player.getService().submit(this::save);
    }

    /**
     * Deserializes all persistent data and verifies the password.
     *
     * @param enteredPassword The entered password.
     * @return The login response.
     */
    public LoginResponse load(String enteredPassword) {
        if (!Files.exists(path)) {
            player.setPosition(LunaConstants.STARTING_POSITION);
            return LoginResponse.NORMAL;
        }

        try (Reader reader = new FileReader(path.toFile())) {
            JsonObject jsonReader = (JsonObject) new JsonParser().parse(reader);

            String password = jsonReader.get("password").getAsString();
            if (!checkPassword(enteredPassword, password)) {
                return LoginResponse.INVALID_CREDENTIALS;
            }

            Position position = getAsType(jsonReader.get("position"), Position.class);
            player.setPosition(position);

            PlayerRights rights = PlayerRights.valueOf(jsonReader.get("rights").getAsString());
            player.setRights(rights);

            boolean running = jsonReader.get("running").getAsBoolean();
            player.getWalking().setRunning(running);

            int[] appearance = getAsType(jsonReader.get("appearance"), int[].class);
            player.getAppearance().setValues(appearance);

            IndexedItem[] inventory = getAsType(jsonReader.get("inventory"), IndexedItem[].class);
            player.getInventory().init(inventory);

            IndexedItem[] bank = getAsType(jsonReader.get("bank"), IndexedItem[].class);
            player.getBank().init(bank);

            IndexedItem[] equipment = getAsType(jsonReader.get("equipment"), IndexedItem[].class);
            player.getEquipment().init(equipment);

            Skill[] skills = getAsType(jsonReader.get("skills"), Skill[].class);
            player.getSkills().setSkills(skills);
            
            long[] friends = getAsType(jsonReader.get("friends"), long[].class);
            player.setFriends(friends);

            long[] ignores = getAsType(jsonReader.get("ignores"), long[].class);
            player.setIgnores(ignores);

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
