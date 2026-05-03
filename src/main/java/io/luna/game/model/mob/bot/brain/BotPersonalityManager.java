package io.luna.game.model.mob.bot.brain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.bot.Bot;
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
        JOKER,
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
     * Defines archetypal armor and gear sets that bots can reference.
     * <p>
     * These enums can be used for loadout generation, equipment selection, or visual identity purposes.
     */
    public enum GearSetType {

        // Melee - regular metal armor.
        BRONZE_FULL_LEGS,
        BRONZE_FULL_SKIRT,
        IRON_FULL_LEGS,
        IRON_FULL_SKIRT,
        STEEL_FULL_LEGS,
        STEEL_FULL_SKIRT,
        BLACK_FULL_LEGS,
        BLACK_FULL_SKIRT,
        MITHRIL_FULL_LEGS,
        MITHRIL_FULL_SKIRT,
        ADAMANT_FULL_LEGS,
        ADAMANT_FULL_SKIRT,
        RUNE_FULL_LEGS,
        RUNE_FULL_SKIRT,
        DRAGON_FULL_LEGS,
        DRAGON_FULL_SKIRT,
        OBSIDIAN_FULL,

        // Melee - Barrows.
        DHAROKS,
        VERACS,
        TORAGS,
        GUTHANS,

        // Ranged.
        LEATHER,
        STUDDED_LEATHER,
        GREEN_DHIDE,
        BLUE_DHIDE,
        RED_DHIDE,
        BLACK_DHIDE,
        KARILS,

        // Magic.
        WIZARD_BLUE,
        WIZARD_DARK,
        MYSTIC_LIGHT,
        MYSTIC_DARK,
        AHRIMS,

        // Show-off armor - trimmed and gold.
        BLACK_TRIMMED_LEGS,
        BLACK_TRIMMED_SKIRT,
        BLACK_GOLD_LEGS,
        BLACK_GOLD_SKIRT,
        ADAMANT_TRIMMED_LEGS,
        ADAMANT_TRIMMED_SKIRT,
        ADAMANT_GOLD_LEGS,
        ADAMANT_GOLD_SKIRT,
        RUNE_TRIMMED_LEGS,
        RUNE_TRIMMED_SKIRT,
        RUNE_GOLD_LEGS,
        RUNE_GOLD_SKIRT,

        // Show-off armor - god rune.
        RUNE_SARADOMIN_LEGS,
        RUNE_SARADOMIN_SKIRT,
        RUNE_GUTHIX_LEGS,
        RUNE_GUTHIX_SKIRT,
        RUNE_ZAMORAK_LEGS,
        RUNE_ZAMORAK_SKIRT,

        // Show-off armor - random event / cosmetic sets.
        ZAMORAK_ROBES,
        MIME,
        LEDERHOSEN,
        FROG_PRINCE,
        FROG_PRINCESS,
        PIRATE_A,
        PIRATE_B,
        PIRATE_C,
        PIRATE_D,

        // Show-off armor - gilded rune.
        GILDED_RUNE_LEGS,
        GILDED_RUNE_SKIRT;
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
        final GearSetType type;
        final ImmutableSet<Integer> items;
        final int level;
        final ImmutableSet<GearSetPurpose> purposes;

        public GearSet(GearSetType type, ImmutableSet<Integer> items, int level, ImmutableSet<GearSetPurpose> purposes) {
            this.type = type;
            this.items = items;
            this.level = level;
            this.purposes = purposes;
        }
    }

    /**
     * The resolved path to the JSON file containing {@link PersonalityTemplate} definitions.
     */
    private static final Path PERSONALITIES_PATH;

    /**
     * The resolved path to the JSON file containing {@link GearSet} definitions.
     */
    private static final Path GEAR_SET_PATH;

    static {
        PERSONALITIES_PATH = Paths.get("data", "game", "bots", "personalities.jsonc");
        GEAR_SET_PATH = Paths.get("data", "game", "bots", "items", "gear_sets.jsonc");
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
     * A map of all loaded {@link GearSet} definitions keyed by their {@link GearSetType}.
     * <p>
     * This collection allows bots to quickly retrieve preconfigured equipment loadouts appropriate for their purpose,
     * such as training, PvP, or social display.
     * <p>
     * Example usage:
     * <pre>
     * {@code GearSet runeSet = personalityManager.getGearSet(GearSetType.RUNE_FULL);}
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
     * Loads and parses all templates from {@link #PERSONALITIES_PATH}.
     * <p>
     * Templates are expected to be defined as an array of JSON objects, each with field matching those in
     * {@link PersonalityTemplate}.
     */
    private void loadTemplates() {
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
     * Loads and parses all gear sets from {@link #GEAR_SET_PATH}.
     * <p>
     * Gear sets are expected to be defined as an array of JSON objects, each with field matching those in {@link GearSet}.
     */
    private void loadGearSets() {
        try {
            JsonArray array = GsonUtils.readAsType(GEAR_SET_PATH, JsonArray.class);
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                GearSetType type = GearSetType.valueOf(object.get("name").getAsString());
                ImmutableSet.Builder<Integer> items = ImmutableSet.builder();
                int level = object.get("level").getAsInt();
                ImmutableSet<GearSetPurpose> purposes =
                        Streams.stream(object.get("purposes").getAsJsonArray().iterator())
                                .map(it -> GearSetPurpose.valueOf(it.getAsString()))
                                .collect(ImmutableSet.toImmutableSet());
                for (JsonElement itemElement : object.get("items").getAsJsonArray()) {
                    if (itemElement.isJsonPrimitive()) {
                        int id = itemElement.getAsJsonPrimitive().isString() ?
                                Item.findId(itemElement.getAsString(), false) : itemElement.getAsInt();
                        items.add(id);
                    }
                }
                gearSetMap.put(type, new GearSet(type, items.build(), level, purposes));
            }
            logger.debug("Loaded {} gear set templates.", array.size());
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