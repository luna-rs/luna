package io.luna.game.model.mob.bot.brain;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.luna.util.GsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the loading and retrieval of predefined templates that influence bot personalities.
 * <p>
 * {@link PersonalityTemplate}s define the behavioral archetypes that bots can be constructed from. These templates also
 * provide the base values for the core traits ({@code intelligence}, {@code kindness}, {@code confidence}, {@code social},
 * {@code dexterity}) and can also specify preferred {@link BotActivity} weights.
 * <p>
 * Templates are stored as JSON files and automatically loaded at runtime from {@code data/game/bots/personality/templates.json}.
 * <p>
 * Additionally, this class defines reference enums such as {@link GearSetType} and {@link GearSetPurpose}, which
 * describe equipment archetypes and their primary use cases.
 *
 * @author lare96
 */
public class BotPersonalityManager {

    /**
     * Enumerates all predefined {@link BotPersonality} archetypes.
     * <p>
     * Each template defines a unique behavioral identity that influences the bot's decision-making, emotional tendencies,
     * and activity preferences.
     */
    public enum PersonalityTemplateType {

        // TODO Serialize template type so it can influence decisions made?

        /**
         * A bot focused heavily on skilling activities.
         * <p>
         * Skillers usually prefer gathering, production, and other non-combat progression. They tend to have higher dexterity
         * so they make more efficient skilling choices.
         */
        SKILLER,

        /**
         * A bot focused heavily on player-killing and combat.
         * <p>
         * PKers tend to be more aggressive, less kind, and more intelligent when making combat or Wilderness-related decisions.
         */
        PKER,

        /**
         * A bot focused heavily on trading and economy activity.
         * <p>
         * Merchants prefer buying, selling, flipping, and other market-driven behaviour.
         */
        MERCHANT,

        /**
         * A highly active bot with strong overall behaviour.
         * <p>
         * No-life bots are meant to feel like dedicated players that spend a lot of time online and generally make effective
         * decisions.
         */
        NO_LIFE,

        /**
         * A highly active bot with weaker decision-making.
         * <p>
         * Kid bots are meant to feel inexperienced, impulsive, or inefficient while still spending a lot of time online.
         */
        KID,

        /**
         * A heroic social bot archetype.
         * <p>
         * Heroes are highly kind, social, intelligent, and dexterous. They should tend toward helpful, impressive, or
         * protective behaviour.
         */
        HERO,

        /**
         * A balanced bot archetype with broad interests.
         * <p>
         * Jack-of-all-trades bots are reasonably capable across many activities and do not strongly specialize in one playstyle.
         */
        JACK_OF_ALL_TRADES,

        /**
         * A bot focused on manipulative trading and scam-like behaviour.
         * <p>
         * Scammers are highly intelligent and social, but low in kindness. They strongly prefer trading-related activity and
         * social opportunities where scams can be attempted.
         */
        SCAMMER;

        /**
         * An immutable cache of {@link #values()}.
         */
        public static final ImmutableList<PersonalityTemplateType> ALL = ImmutableList.copyOf(values());
    }

    /**
     * Represents a single personality template definition.
     * <p>
     * A template defines the core trait distribution for a behavioral archetype, along with a descriptive label and
     * optional preferred activity weights.
     */
    public static final class PersonalityTemplate {
        final PersonalityTemplateType type;
        final String description;
        final double intelligence;
        final double kindness;
        final double confidence;
        final double social;
        final double dexterity;
        final Map<BotActivity, Double> activities;

        public PersonalityTemplate(PersonalityTemplateType type, String description, double intelligence, double kindness,
                                   double confidence, double social, double dexterity, Map<BotActivity, Double> activities) {
            this.type = type;
            this.description = description;
            this.intelligence = intelligence;
            this.kindness = kindness;
            this.confidence = confidence;
            this.social = social;
            this.dexterity = dexterity;
            this.activities = activities;
        }
    }


    /**
     * The resolved path to the JSON file containing {@link PersonalityTemplate} definitions.
     */
    private static final Path PERSONALITIES_PATH;


    static {
        PERSONALITIES_PATH = Paths.get("data", "game", "bots", "personalities.jsonc");
    }

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * A map of all loaded {@link PersonalityTemplate} instances keyed by their corresponding
     * {@link PersonalityTemplateType}.
     * <p>
     * Once populated, this structure allows rapid retrieval of template data for use in
     * {@link BotPersonality.Builder#template(PersonalityTemplateType)} and other systems.
     * <p>
     * Example usage:
     * <pre>
     * {@code PersonalityTemplate template = personalityManager.getTemplate(PersonalityTemplateType.PKER);}
     * </pre>
     */
    private final Map<PersonalityTemplateType, PersonalityTemplate> templateMap =
            new EnumMap<>(PersonalityTemplateType.class);

    /**
     * Loads all bot personality data.
     * <p>
     * This method should be invoked during game initialization or server startup.
     */
    public void load() {
        try {
            JsonArray array = GsonUtils.readAsType(PERSONALITIES_PATH, JsonArray.class);
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                PersonalityTemplateType type = PersonalityTemplateType.valueOf(object.get("type").getAsString());
                String description = object.get("description").getAsString();
                double intelligence = object.get("intelligence").getAsDouble();
                double kindness = object.get("kindness").getAsDouble();
                double confidence = object.get("confidence").getAsDouble();
                final double social = object.get("social").getAsDouble();
                final double dexterity = object.get("dexterity").getAsDouble();

                JsonObject activitiesJson = object.getAsJsonObject("preferences");
                Map<BotActivity, Double> activities = new HashMap<>();
                for (var entry : activitiesJson.entrySet()) {
                    BotActivity activity = BotActivity.valueOf(entry.getKey());
                    activities.put(activity, entry.getValue().getAsDouble());
                }
                templateMap.put(type, new PersonalityTemplate(type, description, intelligence, kindness, confidence,
                        social, dexterity, activities));
            }
            logger.debug("Loaded {} personality templates.", templateMap.size());
        } catch (Exception e) {
            logger.error("Failed to load personality templates!", e);
        }
    }

    /**
     * Retrieves a loaded {@link PersonalityTemplate} by its type.
     *
     * @param type The template type to look up.
     * @return The corresponding template, or {@code null} if not found.
     */
    public PersonalityTemplate getTemplate(PersonalityTemplateType type) {
        return templateMap.get(type);
    }
}