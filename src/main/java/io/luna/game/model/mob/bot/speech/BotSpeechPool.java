package io.luna.game.model.mob.bot.speech;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.luna.util.GsonUtils;
import io.luna.util.RandomUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map.Entry;

/**
 * A helper class that loads files within {@code ./data/game/bots/speech/} into memory. These files, when loaded are
 * referred to as speech pools. Speech pools provide the base phrases that bots will speak. Whether they're randomly
 * talking or reacting to events in the game world.
 *
 * @param <T> The {@link Enum} type contextualising this pool.
 */
public class BotSpeechPool<T extends Enum<T>> {

    /**
     * Path to the folder containing data for speech pools.
     */
    private static final Path BASE_PATH = Paths.get("data", "game", "bots", "speech");

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The path to the pool.
     */
    private final Path path;

    /**
     * The enum type contextualising the phrases.
     */
    private final Class<T> enumType;

    /**
     * The speech pool, in memory.
     */
    private final ListMultimap<T, String> pool = ArrayListMultimap.create();

    /**
     * Creates a new {@link BotSpeechPool}.
     *
     * @param path The path to the pool.
     * @param enumType The enum type contextualising the phrases.
     */
    public BotSpeechPool(Path path, Class<T> enumType) {
        this.path = BASE_PATH.resolve(path);
        this.enumType = enumType;
    }

    /**
     * Loads the speech pool into memory.
     */
    public final void load() {
        try (BufferedReader br = Files.newBufferedReader(path)) {
            JsonObject object = GsonUtils.GSON.fromJson(br, JsonObject.class);
            for (Entry<String, JsonElement> entry : object.entrySet()) {
                T value = Enum.valueOf(enumType, entry.getKey());
                JsonElement arrayElement = object.get(value.name());
                if (arrayElement != null && !arrayElement.isJsonNull()) {
                    for (JsonElement phraseElement : arrayElement.getAsJsonArray()) {
                        pool.put(value, phraseElement.getAsString());
                    }
                }
            }
        } catch (Exception e) {
            logger.catching(e);
        }
    }

    /**
     * Retrieves a random phrase from the pool based on the {@code context}.
     *
     * @param context The type of phrase to retrieve.
     * @return The phrase, {@code null} if no phrases exist for this context.
     */
    public final String take(T context) {
        return RandomUtils.random(pool.get(context));
    }
}
