package io.luna.game.model.mobile;

import io.luna.game.GameService;
import io.luna.net.codec.login.LoginResponse;
import io.luna.util.yaml.YamlDocument;

import java.io.FileWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import com.google.common.base.CaseFormat;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Functions that allow for synchronous and asynchronous serialization and
 * deserialization of a {@link Player}.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerSerializer {

    /**
     * A model representing a persistent {@link Field} within the {@link Player}
     * class.
     * 
     * @author lare96 <http://github.org/lare96>
     */
    public static final class PersistentField {

        /**
         * The name of the persistent field.
         */
        private final String fieldName;

        /**
         * The token of the persistent field.
         */
        private final String tokenName;

        /**
         * Creates a new {@link PersistentField}.
         *
         * @param fieldName The name of the persistent field.
         */
        public PersistentField(String fieldName) {
            this.fieldName = fieldName;
            tokenName = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, fieldName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fieldName);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof PersistentField) {
                PersistentField other = (PersistentField) obj;
                return fieldName.equals(other.fieldName);
            }
            return false;
        }
    }

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(PlayerSerializer.class);

    /**
     * A {@link LinkedHashSet} of {@link PersistentField}s for serialization and
     * deserialization.
     */
    public static final Set<PersistentField> PERSISTENT_FIELDS = new LinkedHashSet<>();

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
        path = Paths.get("./data/players/" + player.getUsername() + ".yml");
    }

    /**
     * Attempts to serialize all persistent data for the {@link Player}.
     */
    public void save() {
        try {
            Yaml yml = new Yaml();
            YamlDocument to = new YamlDocument();

            for (PersistentField it : PERSISTENT_FIELDS) {
                Field field = Player.class.getField(it.fieldName);
                field.setAccessible(true);
                to.add(it.tokenName, field.get(player));
            }
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
            Yaml yml = new Yaml();
            YamlDocument from = new YamlDocument((Map<String, Object>) yml.load(Files.newBufferedReader(path)));

            if (!expectedPassword.equals(from.get("password").asString())) {
                return LoginResponse.INVALID_CREDENTIALS;
            }

            for (PersistentField it : PERSISTENT_FIELDS) {
                Field field = Player.class.getField(it.fieldName);
                field.setAccessible(true);
                field.set(player, from.get(it.tokenName));
            }
        } catch (Exception e) {
            LOGGER.catching(Level.WARN, e);
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
