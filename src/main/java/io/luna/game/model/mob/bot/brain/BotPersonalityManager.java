package io.luna.game.model.mob.bot.brain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.mob.bot.Bot;
import io.luna.util.GsonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;

/**
 * Manages the loading and retrieval of predefined templates that influence bot personalities.
 * <p>
 * {@link PersonalityTemplate}s define the behavioral archetypes that bots can be constructed from. These templates also
 * provide the base values for the core traits ({@code intelligence}, {@code kindness}, {@code confidence},
 * {@code social}, {@code dexterity}) and can also specify preferred {@link BotActivity} weights.
 *
 * <p>Templates are stored as JSON files and automatically loaded at runtime
 * from {@code data/game/bots/personality/templates.json}.
 *
 * <p>Additionally, this class defines reference enums such as {@link GearSetType}
 * and {@link GearSetPurpose}, which describe equipment archetypes and their primary use cases.
 *
 * @author lare96
 */
public class BotPersonalityManager {

    /**
     * Enumerates all predefined {@link BotPersonality} archetypes.
     * <p>
     * Each template defines a unique behavioral identity that influences the bot's decision-making, emotional
     * tendencies, and activity preferences.
     */
    public enum PersonalityTemplateType {
        EFFICIENT,
        GATHERER,
        FIGHTER,
        TRADER,
        SOCIALIZER,
        PKER,
        GRINDER,
        CASUAL,
        RISK_TAKER,
        MINIGAMER,
        LONE_WOLF,
        HELPER,
        HOARDER,
        EXPLORER,
        COLLECTOR,
        GAMBLER,
        AFKER,
        SCHOLAR,
        JOKER;

        /**
         * An immutable cache of {@link #values()}.
         */
        public static final ImmutableList<PersonalityTemplateType> ALL = ImmutableList.copyOf(values());
    }

    /**
     * Represents a single personality template definition.
     * <p>
     * A template defines the core trait distribution for a behavioral archetype, along with a descriptive label
     * and optional preferred activity weights.
     */
    public static final class PersonalityTemplate {
        final String name;
        final String description;
        final double intelligence;
        final double kindness;
        final double confidence;
        final double social;
        final double dexterity;
        final Map<BotActivity, Double> activities;

        public PersonalityTemplate(String name, String description, double intelligence, double kindness,
                                   double confidence, double social, double dexterity, Map<BotActivity, Double> activities) {
            this.name = name;
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
     * Defines archetypal armor and gear sets that bots can reference.
     * <p>
     * These enums can be used for loadout generation or visual identity purposes.
     */
    public enum GearSetType {
        // Melee
        BRONZE_FULL, IRON_FULL, STEEL_FULL, BLACK_FULL, MITHRIL_FULL, ADAMANT_FULL, RUNE_FULL, DRAGON_FULL,
        OBSIDIAN_FULL,
        DHAROKS, VERACS, TORAGS, GUTHANS,

        // Ranged
        LEATHER_SET, STUDDED_LEATHER_SET,
        GREEN_DHIDE_SET, BLUE_DHIDE_SET, RED_DHIDE_SET, BLACK_DHIDE_SET,
        KARILS_SET,

        // Magic
        WIZARD_BLUE_SET, WIZARD_DARK_SET,
        MYSTIC_LIGHT_SET, MYSTIC_DARK_SET,
        AHRIMS_SET,

        // Show-off armor
        BLACK_TRIMMED,
        BLACK_GOLD,
        MITHRIL_TRIMMED,
        MITHRIL_GOLD,
        ADAMANT_TRIMMED,
        ADAMANT_GOLD,
        RUNE_TRIMMED,
        RUNE_GOLD,
        RUNE_SARADOMIN,
        RUNE_GUTHIX,
        RUNE_ZAMORAK,
        GILDED_RUNE;
    }


    /**
     * Describes the general purpose of a gear set.
     */
    public enum GearSetPurpose {

        /**
         * Primarily for PvP combat.
         */
        PKING,

        /**
         * Used for PvE or skill training.
         */
        TRAINING,

        /**
         * Worn for social status or aesthetics.
         */
        SHOW_OFF,

        /**
         * Defensive or tank-oriented setups.
         */
        DEFENSIVE,

        /**
         * Focused on magic combat.
         */
        MAGIC,

        /**
         * Focused on ranged combat.
         */
        RANGE,

        /**
         * Focused on melee combat.
         */
        MELEE,

        /**
         * Used for PvM boss encounters.
         */
        BOSSES,

        /**
         * Suitable for Slayer or task-based combat.
         */
        SLAYER,

        /**
         * Hybrid (multi-style) setups.
         */
        HYBRID;
    }

    /**
     * Represents a single gear set definition.
     * <p>
     * A gear set defines a named collection of items that together form a recognizable loadout or archetype for
     * a {@link Bot}. Gear sets are primarily used by the bot equipment system to select appropriate loadouts based on
     * the bot’s level, purpose, and personality traits.
     */
    public static final class GearSet {
        final String name;
        final ImmutableSet<Integer> items;
        final int level;
        final ImmutableSet<GearSetPurpose> purposes;

        public GearSet(String name, ImmutableSet<Integer> items, int level, ImmutableSet<GearSetPurpose> purposes) {
            this.name = name;
            this.items = items;
            this.level = level;
            this.purposes = purposes;
        }
    }

    /**
     * The base directory path containing all bot personality and equipment configuration files.
     */
    private static final Path BASE_PATH = Paths.get("data", "game", "bots", "personality");

    /**
     * The resolved path to the JSON file containing {@link PersonalityTemplate} definitions.
     */
    private static final Path TEMPLATES_PATH;

    /**
     * The resolved path to the JSON file containing {@link GearSet} definitions.
     */
    private static final Path GEAR_SET_PATH;

    static {
        TEMPLATES_PATH = BASE_PATH.resolve("templates.json");
        GEAR_SET_PATH = BASE_PATH.resolve("gearsets.json");
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
     *
     * <p>Example usage:
     * <pre>
     *     PersonalityTemplate template = personalityManager.getTemplate(PersonalityTemplateType.PKER);
     * </pre>
     */
    private final Map<PersonalityTemplateType, PersonalityTemplate> templateMap =
            new EnumMap<>(PersonalityTemplateType.class);

    /**
     * A map of all loaded {@link GearSet} definitions keyed by their {@link GearSetType}.
     * <p>
     * This collection allows bots to quickly retrieve preconfigured equipment loadouts
     * appropriate for their purpose, such as training, PvP, or social display.
     *
     * <p>Example usage:
     * <pre>
     *     GearSet runeSet = personalityManager.getGearSet(GearSetType.RUNE_FULL);
     * </pre>
     */
    private final Map<GearSetType, GearSet> gearSetMap = new EnumMap<>(GearSetType.class);

    /**
     * Loads all bot personality data.
     * <p>
     * This method should be invoked during game initialization or server startup.
     */
    public void load() {
        loadTemplates();
        loadGearSets();
    }

    /**
     * Loads and parses all templates from {@link #TEMPLATES_PATH}.
     * <p>
     * Templates are expected to be defined as an array of JSON objects, each with field matching those in
     * {@link PersonalityTemplate}.
     */
    public void loadTemplates() {
        try {
            PersonalityTemplate[] loadedTemplates = GsonUtils.readAsType(TEMPLATES_PATH, PersonalityTemplate[].class);
            for (PersonalityTemplate template : loadedTemplates) {
                String name = template.name;
                try {
                    PersonalityTemplateType type = PersonalityTemplateType.valueOf(name);
                    templateMap.put(type, template);
                } catch (IllegalArgumentException e) {
                    logger.error(new ParameterizedMessage(
                            "Couldn't load personality template [{}]. Type not recognized.", name), e);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load personality templates!", e);
        }
    }

    /**
     * Loads and parses all gear sets from {@link #GEAR_SET_PATH}.
     * <p>
     * Gear sets are expected to be defined as an array of JSON objects, each with field matching those
     * in {@link GearSet}.
     */
    private void loadGearSets() {
        try {
            GearSet[] loadedTemplates = GsonUtils.readAsType(GEAR_SET_PATH, GearSet[].class);
            for (GearSet set : loadedTemplates) {
                String name = set.name;
                try {
                    GearSetType type = GearSetType.valueOf(name);
                    gearSetMap.put(type, set);
                } catch (IllegalArgumentException e) {
                    logger.error(new ParameterizedMessage(
                            "Couldn't load gear set [{}]. Type not recognized.", name), e);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load gear sets!", e);
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

    /**
     * Retrieves a loaded {@link GearSet} by its type.
     *
     * @param type The gear set type to look up.
     * @return The corresponding gear set, or {@code null} if not found.
     */
    public GearSet getGearSet(GearSetType type) {
        return gearSetMap.get(type);
    }
}