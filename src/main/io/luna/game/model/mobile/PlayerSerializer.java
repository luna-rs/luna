package io.luna.game.model.mobile;

import io.luna.game.GameService;
import io.luna.game.model.Position;
import io.luna.net.codec.login.LoginResponse;
import io.luna.util.yaml.YamlDocument;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Functions that allow for synchronous and asynchronous serialization and
 * deserialization of a {@link Player}.
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
    private static final Path FILE_DIR = Paths.get("./data/player_files");

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
        path = FILE_DIR.resolve(player.getUsername() + ".yml");
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
        try {
            Yaml yml = new Yaml();
            YamlDocument to = new YamlDocument();

            to.add("password", player.getPassword());
			to.add("position", player.getPosition());
			to.add("rights", player.getRights());

            yml.dump(to.toSerializableMap(), new FileWriter(path.toFile()));
        } catch (Exception e) {
            LOGGER.catching(Level.WARN, e);
        }
    }

    /**
     * Attempts to asynchronously serialize all persistent data for the
     * {@link Player}. Returns a {@link ListenableFuture} detailing the progress
     * and result of the asynchronous task.
     * 
     * @param service The {@link GameService} to use for asynchronous execution.
     * @return The {@link ListenableFuture} detailing progress and the result.
     */
    public ListenableFuture<Void> asyncSave(GameService service) {
        return service.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                save();
                return null;
            }
        });
    }

    /**
     * Attempts to deserialize all persistent data for the {@link Player}.
     * 
     * @param expectedPassword The expected password to be compared against the
     *        deserialized password.
     * @return The {@link LoginResponse} determined by the deserialization.
     */
    @SuppressWarnings("unchecked")
    public LoginResponse load(String expectedPassword) {
        try {
            if (!path.toFile().exists()) {
                return LoginResponse.NORMAL;
            }

            Yaml yml = new Yaml();
            YamlDocument from = new YamlDocument((Map<String, Object>) yml.load(Files.newBufferedReader(path)));

			String password = from.get("password").asString();
			Position position = from.get("position").asType(Position.class);
			PlayerRights rights = PlayerRights.valueOf(from.get("rights").asString());

			player.setPosition(position);
			player.setRights(rights);

			if (!expectedPassword.equals(password)) {
                return LoginResponse.INVALID_CREDENTIALS;
            }
        } catch (Exception e) {
			LOGGER.catching(e);
            return LoginResponse.COULD_NOT_COMPLETE_LOGIN;
        }
        return LoginResponse.NORMAL;
    }

    /**
     * Attempts to asynchronously deserialize all persistent data for the
     * {@link Player}. Returns a {@link ListenableFuture} detailing the progress
     * and result of the asynchronous task.
     * 
     * @param expectedPassword The expected password to be compared against the
     *        deserialized password.
     * @param service The {@link GameService} to use for asynchronous execution.
     * @return The {@link ListenableFuture} detailing progress and the result.
     */
    public ListenableFuture<LoginResponse> asyncLoad(String expectedPassword, GameService service) {
        return service.submit(new Callable<LoginResponse>() {
            @Override
            public LoginResponse call() throws Exception {
                return load(expectedPassword);
            }
        });
    }
}
