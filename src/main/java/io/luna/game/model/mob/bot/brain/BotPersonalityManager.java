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
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and stores predefined bot personality templates.
 * <p>
 * Personality templates describe broad bot archetypes such as skillers, PKers, merchants, and scammers. Each
 * template provides base values for the core personality traits used by {@link BotPersonality}, along with weighted
 * activity preferences that influence what the bot is likely to do.
 * <p>
 * Templates are loaded from {@code data/game/bots/personalities.jsonc} during startup and can later be queried by
 * their {@link PersonalityTemplateType}.
 *
 * @author lare96
 */
public class BotPersonalityManager {

    /**
     * The available predefined personality template types.
     * <p>
     * Each type represents a high-level behavioral archetype. The actual trait values and activity preferences for
     * each type are loaded from the personality template JSON file.
     */
    public enum PersonalityTemplateType {

        /**
         * No personality template.
         *
         * <p>This is used when a bot should not apply one of the predefined archetypes.
         */
        NONE,

        /**
         * A bot focused on skilling activities.
         *
         * <p>Skillers usually prefer gathering, production, and other non-combat progression.
         */
        SKILLER,

        /**
         * A bot focused on player-killing and combat.
         *
         * <p>PKers usually prefer combat, Wilderness activity, and aggressive player interaction.
         */
        PKER,

        /**
         * A bot focused on trading and economy activity.
         *
         * <p>Merchants usually prefer buying, selling, flipping, and other market-driven behavior.
         */
        MERCHANT,

        /**
         * A highly active bot with strong general behavior.
         *
         * <p>No-life bots are meant to feel like dedicated players that spend a lot of time online and make generally
         * effective decisions.
         */
        NO_LIFE,

        /**
         * A highly active bot with weaker decision-making.
         *
         * <p>Kid bots are meant to feel inexperienced, impulsive, inefficient, or chaotic while still spending a lot of
         * time online.
         */
        KID,

        /**
         * A heroic social bot archetype.
         *
         * <p>Heroes tend toward helpful, impressive, protective, or socially positive behavior.
         */
        HERO,

        /**
         * A balanced bot archetype with broad interests.
         *
         * <p>Jack-of-all-trades bots are reasonably capable across many activities and do not strongly specialize in one
         * playstyle.
         */
        JACK_OF_ALL_TRADES,

        /**
         * A bot focused on manipulative trading and scam-like behavior.
         *
         * <p>Scammers usually have low kindness and strong trading or social preferences.
         */
        SCAMMER;

        /**
         * All template types except {@link #NONE}.
         *
         * <p>This is useful when randomly selecting a real personality archetype.
         */
        public static final ImmutableList<PersonalityTemplateType> ALL_EXCEPT_NONE = Arrays.stream(values())
                .filter(it -> it != NONE)
                .collect(ImmutableList.toImmutableList());
    }

    /**
     * A loaded personality template definition.
     *
     * <p>A template defines the base trait values and activity preferences for one personality archetype. These values
     * are applied by {@link BotPersonality.Builder#template(PersonalityTemplateType)} when constructing a bot
     * personality.
     */
    public static final class PersonalityTemplate {

        /**
         * The archetype this template represents.
         */
        final PersonalityTemplateType type;

        /**
         * A short human-readable description of the template.
         */
        final String description;

        /**
         * The base intelligence trait value.
         */
        final double intelligence;

        /**
         * The base kindness trait value.
         */
        final double kindness;

        /**
         * The base confidence trait value.
         */
        final double confidence;

        /**
         * The base social trait value.
         */
        final double social;

        /**
         * The base dexterity trait value.
         */
        final double dexterity;

        /**
         * The activity preference weights for this template.
         */
        final Map<BotActivity, Double> activities;

        /**
         * Creates a new personality template.
         *
         * @param type The template archetype.
         * @param description A short description of the template.
         * @param intelligence The base intelligence value.
         * @param kindness The base kindness value.
         * @param confidence The base confidence value.
         * @param social The base social value.
         * @param dexterity The base dexterity value.
         * @param activities The activity preference weights.
         */
        public PersonalityTemplate(PersonalityTemplateType type, String description, double intelligence, double kindness,
                                   double confidence, double social, double dexterity,
                                   Map<BotActivity, Double> activities) {
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
     * The path to the personality template JSON file.
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
     * The loaded personality templates, indexed by template type.
     */
    private final Map<PersonalityTemplateType, PersonalityTemplate> templateMap =
            new EnumMap<>(PersonalityTemplateType.class);

    /**
     * Loads all personality templates from disk.
     *
     * <p>This should be called during server startup before bots are created from predefined personality templates.
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
     * Returns the loaded template for the specified type.
     *
     * @param type The template type to look up.
     * @return The loaded template, or {@code null} if no template exists for {@code type}.
     */
    public PersonalityTemplate getTemplate(PersonalityTemplateType type) {
        return templateMap.get(type);
    }
}