package io.luna.game.model.mob.bot.speech;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.luna.game.model.mob.bot.Bot;
import io.luna.util.GsonUtils;
import io.luna.util.RandomUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Function;

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
     * The dynamic tags.
     */
    private final Map<String, Function<Bot, Object>> tags = new HashMap<>();

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
     * Creates a new {@link BotSpeechPool}.
     *
     * @param fileName The file name of the pool.
     * @param enumType The enum type contextualising the phrases.
     */
    public BotSpeechPool(String fileName, Class<T> enumType) {
        this(Paths.get(fileName), enumType);
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
     * Defines a dynamic tag within this speech pool. Tags work like variables that can be substituted
     * into phrases at runtime, allowing bots to inject context-specific information into their speech.
     *
     * <p>For example, given the phrase:</p>
     * <pre><code>
     *     "My name is {name}, my {skill} level is {level}."
     * </code></pre>
     * You can register tags like:
     * <pre><code>
     *     speechPool.setTag("name", bot -> bot.getUsername());
     *     speechPool.setTag("skill", bot -> Skill.name(skillId));
     *     speechPool.setTag("level", bot -> bot.getSkill(skillId).getLevel());
     * </code></pre>
     *
     * <p>When processed, the placeholders <code>{name}</code>, <code>{skill}</code>, and
     * <code>{level}</code> will be replaced with their evaluated values for the bot at runtime.</p>
     *
     * @param name The tag identifier (without braces).
     * @param value A function that supplies the runtime value for this tag, given the bot.
     */
    public void setTag(String name, Function<Bot, Object> value) {
        tags.put(name, value);
    }

    /**
     * Clears all dynamic tags from the backing map.
     */
    public void clearTags() {
        tags.clear();
    }

    /**
     * Retrieves a random phrase from the pool based on the {@code context} and {@code bot}.
     *
     * @param bot The bot that will speak this phrase.
     * @param context The type of phrase to retrieve.
     * @return The phrase, {@code null} if no phrases exist for this context.
     */
    public final String take(Bot bot, T context) {
        String phrase = RandomUtils.random(pool.get(context));
        if (phrase != null) {
            for (var entry : tags.entrySet()) {
                Object replaceWith = entry.getValue().apply(bot);
                phrase = phrase.replace(entry.getKey(), Objects.toString(replaceWith));
            }
            return phrase;
        }
        return null;
    }
}
