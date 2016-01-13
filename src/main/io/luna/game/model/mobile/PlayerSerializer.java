package io.luna.game.model.mobile;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.luna.game.GameService;
import io.luna.game.model.Position;
import io.luna.game.model.mobile.attr.AttributeKey;
import io.luna.game.model.mobile.attr.AttributeValue;
import io.luna.net.codec.login.LoginResponse;
import io.luna.util.GsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import static io.luna.util.GsonUtils.GSON;

/**
 * Functions that allow for synchronous and asynchronous serialization and deserialization of a {@link Player}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerSerializer {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(PlayerSerializer.class);

    /**
     * The {@link Path} to all of the serialized {@link Player} data.
     */
    private static final Path FILE_DIR = Paths.get("./data/player_data_files");

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
        JsonObject object = new JsonObject();

        object.addProperty("password", player.getPassword());
        object.add("position", GSON.toJsonTree(player.getPosition()));
        object.add("rights", GSON.toJsonTree(player.getRights()));

        JsonObject attr = new JsonObject();
        for (Entry<AttributeKey<?>, AttributeValue<?>> it : player.attr) {
            AttributeKey<?> key = it.getKey();
            AttributeValue<?> val = it.getValue();

            if (key.isPersistant()) {
                JsonObject typeVal = new JsonObject();

                typeVal.addProperty("type", key.getTypeName());
                typeVal.add("value", GSON.toJsonTree(val.get()));

                attr.add(key.getName(), typeVal);
            }
        }
        object.add("attributes", attr);

        try (BufferedWriter bw = Files.newBufferedWriter(path)) {
            bw.write(GSON.toJson(object));
        } catch (Exception e) {
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

        try (BufferedReader in = Files.newBufferedReader(path)) {
            JsonObject from = (JsonObject) new JsonParser().parse(in);

            String password = from.get("password").getAsString();
            if (!expectedPassword.equals(password)) {
                return LoginResponse.INVALID_CREDENTIALS;
            }

            Position position = GsonUtils.getAsType(from.get("position"), Position.class);
            player.setPosition(position);

            PlayerRights rights = PlayerRights.valueOf(from.get("rights").getAsString());
            player.setRights(rights);

            JsonObject attr = from.get("attributes").getAsJsonObject();
            for (Entry<String, JsonElement> it : attr.entrySet()) {
                JsonObject obj = it.getValue().getAsJsonObject();

                Class<?> type = Class.forName(obj.get("type").getAsString());
                Object data = GsonUtils.getAsType(obj.get("value"), type);

                player.attr.get(it.getKey().intern()).set(data);
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
