package io.luna.game.model.mob.bot.brain;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import engine.bot.gear.BotGearSet;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.def.WantedItemDefinition;
import io.luna.game.model.def.WantedItemDefinition.WantedItemPriority;
import io.luna.game.model.def.WantedItemDefinition.WantedItemTag;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplate;
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType;
import io.luna.util.GsonUtils;
import io.luna.util.RandomUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static io.luna.game.model.def.WantedItemDefinition.DEFAULT_ALWAYS_TAGS;
import static io.luna.util.RandomUtils.randomFrom;
import static io.luna.util.RandomUtils.roll;

/**
 * Represents a {@link Bot}s behavioral preferences.
 * <p>
 * Preferences describe what a bot enjoys doing (activities), what it likes to train (skills), what items it values
 * (regardless of market value), and what equipment it tends to wear. These are influenced by the bot’s
 * {@link BotPersonality} and evolve dynamically at runtime through its {@link BotEmotion} states.
 * <p>
 * For example:
 * <ul>
 *   <li>A highly dexterous bot may enjoy skill-based activities like Crafting and Herblore.</li>
 *   <li>A confident and low-kindness bot might favor combat or PvP-related activities.</li>
 *   <li>Social bots tend to prefer group-oriented activities like minigames and trading.</li>
 * </ul>
 *
 * @author lare96
 */
public final class BotPreference {

    /**
     * Builder for constructing {@link BotPreference} instances.
     * <p>
     * Builders can load personality data from predefined templates, apply variance for individuality, or generate
     * completely random preferences for testing.
     */
    public static final class Builder {
        private final BotPersonality personality;
        private final BotPersonalityManager personalityManager;
        private final Map<BotActivity, Double> activities = new HashMap<>();
        private final Set<Integer> skills = new HashSet<>();
        private final Map<Integer, WantedItemDefinition> wantedItems = new HashMap<>();
        private final Set<BotGearSet> gear = new HashSet<>();
        private final Map<String, Double> playerFeelings = new HashMap<>();

        private final double intelligence;
        private final double kindness;
        private final double confidence;
        private final double social;
        private final double dexterity;

        public Builder(BotPersonalityManager personalityManager, BotPersonality personality) {
            this.personalityManager = personalityManager;
            this.personality = personality;

            intelligence = personality.getIntelligence();
            kindness = personality.getKindness();
            confidence = personality.getConfidence();
            social = personality.getSocial();
            dexterity = personality.getDexterity();
        }

        /**
         * Assigns a weight to a specific {@link BotActivity}.
         * <p>
         * This allows you to manually set how much the bot enjoys or prioritizes a given activity. The value should
         * fall between {@code 0.0} (no interest) and {@code 1.0} (maximum interest).
         *
         * @param activity The activity to assign a weight to.
         * @param value The preference weight between {@code 0.0} and {@code 1.0}.
         * @return This builder for chaining.
         */
        public Builder setActivity(BotActivity activity, double value) {
            activities.put(activity, value);
            return this;
        }

        /**
         * Adds one or more skill identifiers to this bot’s preferred skill list.
         * <p>
         * This allows manual injection of skills that the bot will favor or regularly train.
         *
         * @param skillIds One or more skill IDs to add as preferences.
         * @return This builder for chaining.
         */
        public Builder addSkill(int... skillIds) {
            for (int id : skillIds) {
                skills.add(id);
            }
            return this;
        }

        /**
         * Adds one or more {@link BotGearSet} values to this bot’s preferred equipment list.
         * <p>
         * This allows manual assignment of specific armor or outfit sets that the bot will attempt to equip or seek
         * when generating loadouts.
         *
         * @param gearSets One or more gear set types to add as preferences.
         * @return This builder for chaining.
         */
        public Builder addGear(BotGearSet... gearSets) {
            gear.addAll(Arrays.asList(gearSets));
            return this;
        }

        public Builder addWantedItem(WantedItemDefinition item) {
            wantedItems.put(item.id(), item);
            return this;
        }

        public Builder setFeelingsFor(String username, double feeling) {
            playerFeelings.put(username, Math.min(1.0, Math.max(feeling, 0.0)));
            return this;
        }

        /**
         * Loads activity preferences from a predefined {@link PersonalityTemplateType} without variance.
         *
         * @param from The template type to load from.
         * @return This builder for chaining.
         */
        public Builder template(PersonalityTemplateType from) {
            PersonalityTemplate template = personalityManager.getTemplate(from);
            activities.putAll(template.activities);
            return this;
        }


        /**
         * Randomizes this bot’s personality using a random template type and a dynamic variance value.
         * <p>
         * The variance is chosen randomly between {@code 0.05} and {@code 0.25}, introducing a natural spread of
         * unique personalities without producing extreme outliers.
         *
         * @return This builder instance for chaining.
         */
        public Builder randomizeSmart() {
            double variance = ThreadLocalRandom.current().nextDouble(0.05, 0.25);
            Supplier<Double> varianceSupplier = () -> ThreadLocalRandom.current().nextDouble(-variance, variance);
            PersonalityTemplate template = personalityManager.getTemplate(RandomUtils.random(PersonalityTemplateType.ALL_EXCEPT_NONE));
            for (var entry : template.activities.entrySet()) {
                activities.put(entry.getKey(), entry.getValue() + varianceSupplier.get());
            }
            return this;
        }


        /**
         * Generates a completely random distribution of activity preferences.
         *
         * @return This builder for chaining.
         */
        public Builder randomize() {
            for (BotActivity activity : BotActivity.ALL) {
                activities.put(activity, RandomUtils.nextDouble());
            }
            return this;
        }

        /**
         * Generates a set of skills this bot prefers to train.
         * <p>
         * The final list is influenced by the bot’s personality, favoring skills that align with its intelligence,
         * confidence, social tendencies, and dexterity.
         *
         * @return A generated immutable set of preferred skill IDs.
         */
        private Set<Integer> generateSkills() {
            Set<Integer> selected = new HashSet<>();
            if (skills.isEmpty()) {
                if (personality.isIntelligent()) {
                    selected.addAll(Arrays.asList(Skill.MAGIC, Skill.FLETCHING, Skill.CRAFTING, Skill.FARMING, Skill.RUNECRAFTING,
                            Skill.HERBLORE));
                }
                if (personality.isConfident()) {
                    selected.addAll(Arrays.asList(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE,
                            Skill.HITPOINTS, Skill.SLAYER, Skill.RANGED));
                }
                if (personality.isSocial()) {
                    selected.addAll(Arrays.asList(Skill.FISHING, Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING, Skill.AGILITY));
                }
                if (personality.isDextrous()) {
                    selected.addAll(Arrays.asList(Skill.FLETCHING, Skill.THIEVING, Skill.RANGED, Skill.AGILITY, Skill.CRAFTING,
                            Skill.SMITHING, Skill.WOODCUTTING, Skill.MINING));
                }
                if (personality.isMean()) {
                    selected.add(Skill.THIEVING);
                } else if (personality.isKind()) {
                    selected.add(Skill.PRAYER);
                }

                // Fallback: pick 2–6 random skills
                if (selected.isEmpty()) {
                    for (int loops = 0; loops < RandomUtils.inclusive(2, 6); loops++) {
                        Integer id = RandomUtils.random(Skill.IDS);
                        if (id != null) {
                            selected.add(id);
                        }
                    }
                }
            } else {
                selected.addAll(skills);
            }
            return selected;
        }

        /**
         * Generates a set of preferred gear archetypes based on the bot’s personality traits.
         * <p>
         * Gear preferences are chosen by interpreting the bot’s combat style tendencies and self-image.
         * For example:
         * <ul>
         *   <li>Highly confident, low-kindness bots tend to favor offensive or high-status gear (e.g. {@code DRAGON_FULL}).</li>
         *   <li>Intelligent bots prefer efficient or magically oriented sets (e.g. {@code MYSTIC_LIGHT}, {@code AHRIMS}).</li>
         *   <li>Social bots often choose visually striking "show-off" sets.</li>
         *   <li>Dexterous bots lean toward ranged or lightweight setups.</li>
         * </ul>
         *
         * @return A generated immutable set of {@link BotGearSet}s representing this bot’s favored loadouts.
         */
        private Set<BotGearSet> generateGear() {
            Set<BotGearSet> selected = new HashSet<>();
            if (gear.isEmpty()) {
                if (personality.isConfident() && personality.isMean()) {
                    if (roll(0.20)) {
                        selected.addAll(Arrays.asList(BotGearSet.GUTHANS, BotGearSet.DHAROKS, BotGearSet.VERACS));
                    } else {
                        selected.addAll(Arrays.asList(BotGearSet.DRAGON_FULL_LEGS, BotGearSet.DRAGON_FULL_SKIRT,
                                BotGearSet.AHRIMS, BotGearSet.KARILS));
                    }
                    if (roll(0.10)) {
                        selected.add(randomFrom(BotGearSet.DHAROKS, BotGearSet.VERACS,
                                BotGearSet.TORAGS, BotGearSet.GUTHANS));
                    }
                }

                if (personality.isIntelligent()) {
                    if (roll(0.15)) {
                        selected.add(BotGearSet.AHRIMS);
                    } else if (roll(0.40)) {
                        selected.add(BotGearSet.MYSTIC_DARK);
                    } else {
                        selected.add(BotGearSet.MYSTIC_LIGHT);
                    }

                    // Occasionally add wizard sets for low-level intelligent bots.
                    if (confidence < 0.5) {
                        selected.add(randomFrom(BotGearSet.WIZARD_BLUE, BotGearSet.WIZARD_DARK));
                    }
                }

                // --- Social or aesthetic personalities (show-off) ---
                if (social >= 0.7) {
                    selected.add(randomFrom(
                            BotGearSet.RUNE_TRIMMED_LEGS,
                            BotGearSet.RUNE_TRIMMED_SKIRT,
                            BotGearSet.RUNE_GOLD_LEGS,
                            BotGearSet.RUNE_GOLD_SKIRT,
                            BotGearSet.RUNE_SARADOMIN_LEGS,
                            BotGearSet.RUNE_SARADOMIN_SKIRT,
                            BotGearSet.RUNE_GUTHIX_LEGS,
                            BotGearSet.RUNE_GUTHIX_SKIRT,
                            BotGearSet.RUNE_ZAMORAK_LEGS,
                            BotGearSet.RUNE_ZAMORAK_SKIRT,
                            BotGearSet.GILDED_RUNE_LEGS,
                            BotGearSet.GILDED_RUNE_SKIRT,
                            BotGearSet.BLACK_TRIMMED_LEGS,
                            BotGearSet.BLACK_TRIMMED_SKIRT,
                            BotGearSet.BLACK_GOLD_LEGS,
                            BotGearSet.BLACK_GOLD_SKIRT,
                            BotGearSet.ADAMANT_TRIMMED_LEGS,
                            BotGearSet.ADAMANT_TRIMMED_SKIRT,
                            BotGearSet.ADAMANT_GOLD_LEGS));
                }

                // --- Dexterous bots: light, ranged-focused, practical gear ---
                if (dexterity >= 0.7) {
                    if (roll(0.30)) {
                        selected.add(BotGearSet.KARILS);
                    } else {
                        selected.add(randomFrom(
                                BotGearSet.GREEN_DHIDE,
                                BotGearSet.BLUE_DHIDE,
                                BotGearSet.RED_DHIDE,
                                BotGearSet.BLACK_DHIDE));
                    }

                    // Low-level dexterous bots might wear light armor sets.
                    if (confidence < 0.5) {
                        selected.add(randomFrom(BotGearSet.LEATHER, BotGearSet.STUDDED_LEATHER));
                    }
                }

                // --- Fallback: unremarkable or inexperienced personalities ---
                if (selected.isEmpty()) {
                    selected.addAll(Arrays.asList(BotGearSet.ADAMANT_FULL_LEGS, BotGearSet.ADAMANT_FULL_SKIRT,
                            BotGearSet.RUNE_FULL_LEGS, BotGearSet.RUNE_FULL_SKIRT));
                    selected.add(randomFrom(BotGearSet.KARILS, BotGearSet.DRAGON_FULL_LEGS, BotGearSet.DRAGON_FULL_SKIRT, BotGearSet.BLACK_FULL_LEGS,
                            BotGearSet.GUTHANS, BotGearSet.DHAROKS));
                    return selected;
                }
            } else {
                selected.addAll(gear);
            }
            return selected;
        }

        /**
         * Builds the resulting immutable {@link BotPreference} instance. Generates favorite skills, items, and gear
         * from the bot's personality.
         *
         * @return The fully built preference profile.
         */
        public BotPreference build() {
            return new BotPreference(
                    new HashMap<>(activities),
                    new HashSet<>(generateSkills()),
                    new HashMap<>(wantedItems),
                    new HashSet<>(generateGear()),
                    new HashMap<>(playerFeelings));
        }
    }

    private Bot bot;
    /**
     * The immutable map of activity preference weights.
     */
    private final Map<BotActivity, Double> activities;

    /**
     * The immutable set of preferred skills.
     */
    private final Set<Integer> skills;

    /**
     * The items this bot currently wants to obtain, keyed by unnoted item id.
     * <p>
     * Each definition describes the desired amount and any skill or level restrictions associated with the item.
     * Wanted items may be added or adjusted at runtime as the bot's needs change.
     */
    private final Map<Integer, WantedItemDefinition> wantedItems;

    /**
     * The immutable set of preferred gear archetypes.
     */
    private final Set<BotGearSet> gear;

    /**
     * Stores this bot's long-term feelings toward known players by username.
     * <p>
     * Values are stored on a {@code 0.0} to {@code 1.0} scale:
     * <ul>
     *   <li>{@code 0.0} means the bot strongly hates or distrusts the player.</li>
     *   <li>{@code 0.5} means the bot is neutral or does not know the player.</li>
     *   <li>{@code 1.0} means the bot strongly likes, trusts, or admires the player.</li>
     * </ul>
     */
    private final Map<String, Double> playerFeelings;

    /**
     * Creates a new immutable {@link BotPreference}.
     *
     * @param activities Activity preference weights.
     * @param skills Preferred skill IDs.
     * @param wantedItems Preferred item IDs.
     * @param gear Preferred gear archetypes.
     */
    public BotPreference(Map<BotActivity, Double> activities,
                         Set<Integer> skills,
                         Map<Integer, WantedItemDefinition> wantedItems,
                         Set<BotGearSet> gear,
                         Map<String, Double> playerFeelings) {
        this.activities = new HashMap<>(activities);
        // New and legacy profiles participate in general activities unless an explicit weight is provided.
        this.activities.putIfAbsent(BotActivity.GENERAL_ACTIVITIES, 0.25);
        this.skills = skills;
        this.wantedItems = wantedItems;
        this.gear = gear;
        this.playerFeelings = playerFeelings;
    }

    /**
     * Serializes this preference profile into JSON.
     * <p>
     * Saved data includes activity weights, preferred skills, preferred gear archetypes, and the bot's stored feelings
     * toward known players.
     *
     * @return A JSON object containing this preference profile.
     */
    public JsonObject save() {
        JsonObject preferences = new JsonObject();

        // Serialize activities and their weightings.
        JsonArray activitiesJson = new JsonArray();
        for (var activityEntry : activities.entrySet()) {
            JsonObject activityJson = new JsonObject();
            activityJson.addProperty("type", activityEntry.getKey().name());
            activityJson.addProperty("weight", activityEntry.getValue());
            activitiesJson.add(activityJson);
        }
        preferences.add("activities", activitiesJson);

        // Serialize preferred skills.
        JsonArray skillsJson = new JsonArray();
        skills.forEach(skillsJson::add);
        preferences.add("skills", skillsJson);

        // Serialize wanted items.
        JsonArray wantedItemsJson = new JsonArray();
        wantedItems.values().forEach(it -> {
            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("id", it.id());
            itemJson.addProperty("min", it.min());
            itemJson.addProperty("target", it.target());
            itemJson.addProperty("skill", it.skill());
            itemJson.addProperty("max_level", it.maxLevel());
            wantedItemsJson.add(itemJson);
        });
        preferences.add("wanted_items", wantedItemsJson);

        // Serialize preferred gear.
        JsonArray gearJson = new JsonArray();
        gear.forEach(it -> gearJson.add(it.name()));
        preferences.add("gear", gearJson);

        // Serialize feelings towards players.
        JsonArray playerFeelingsJson = new JsonArray();
        for (var entry : playerFeelings.entrySet()) {
            JsonObject feelingsJson = new JsonObject();
            feelingsJson.addProperty("username", entry.getKey());
            feelingsJson.addProperty("feeling", entry.getValue());
            playerFeelingsJson.add(feelingsJson);
        }
        preferences.add("player_feelings", playerFeelingsJson);
        return preferences;
    }

    /**
     * Loads this preference profile from JSON.
     * <p>
     * Existing activity, skill, gear, and player-feeling collections are populated from the supplied object. The JSON
     * object is expected to follow the same structure produced by {@link #save()}.
     *
     * @param object The JSON object to load preference data from.
     */
    public void load(JsonObject object) {
        // Deserialize activities and their weightings.
        JsonArray activitiesJson = object.getAsJsonArray("activities");
        for (JsonElement activityJson : activitiesJson) {
            JsonObject activityJsonObj = activityJson.getAsJsonObject();
            BotActivity activity = BotActivity.valueOf(activityJsonObj.get("type").getAsString());
            double weight = activityJsonObj.get("weight").getAsDouble();
            activities.put(activity, weight);
        }

        // Deserialize preferred skills and gear, wanted items, and feelings towards players.
        object.getAsJsonArray("skills").forEach(it -> skills.add(it.getAsInt()));
        object.getAsJsonArray("wanted_items").forEach(it -> {
            JsonObject itemJson = it.getAsJsonObject();
            if (itemJson.has("min")) {
                int id = itemJson.get("id").getAsInt();
                int min = itemJson.get("min").getAsInt();
                int target = itemJson.get("target").getAsInt();
                int skill = itemJson.get("skill").getAsInt();
                int maxLevel = itemJson.get("max_level").getAsInt();
                WantedItemPriority priority = WantedItemPriority.valueOf(itemJson.get("priority").getAsString());
                ImmutableSet<WantedItemTag> tags = ImmutableSet.copyOf(GsonUtils.getAsType(itemJson.get("tags"), WantedItemTag[].class));
                wantedItems.put(id, new WantedItemDefinition(id, min, target, skill, maxLevel, priority, tags));
            }
        });
        object.getAsJsonArray("gear").forEach(it -> gear.add(BotGearSet.valueOf(it.getAsString())));
        object.getAsJsonArray("player_feelings").forEach(it -> {
            JsonObject feelingsJson = it.getAsJsonObject();
            playerFeelings.put(feelingsJson.get("username").getAsString(), feelingsJson.get("feeling").getAsDouble());
        });
    }

    /**
     * Adds or replaces an item in this bot's wanted-item list.
     * <p>
     * Noted item ids are normalized to their unnoted form so both versions of an item share the same definition.
     *
     * @param id The item id.
     * @param target The desired amount.
     * @param skill The associated skill id, or {@code -1} if unrestricted.
     * @param maxLevel The maximum skill level this definition applies to, or {@code -1} if unrestricted.
     */
    public void addWantedItem(int id, int target, int skill, int maxLevel) {
        int unnotedId = toUnnotedId(id);
        WantedItemDefinition existing = wantedItems.get(id);
        var tags = existing != null ? existing.tags() :
                WantedItemDefinition.DEFAULT.get(id).map(WantedItemDefinition::tags).orElse(DEFAULT_ALWAYS_TAGS);
        wantedItems.put(unnotedId, new WantedItemDefinition(unnotedId, -1, target, skill, maxLevel, existing != null ? existing.priority() : WantedItemPriority.HIGH, tags));
    }

    /**
     * Adds or replaces an unrestricted item in this bot's wanted-item list.
     *
     * @param id The item id.
     * @param target The desired amount.
     */
    public void addWantedItem(int id, int target) {
        addWantedItem(id, target, -1, -1);
    }

    /**
     * Returns this bot's wanted-item definition for the specified item.
     * <p>
     * Noted item ids are normalized to their unnoted form before lookup.
     *
     * @param id The item id.
     * @return The wanted-item definition, or {@code null} if the bot does not want the item.
     */
    public WantedItemDefinition getWantedItem(int id) {
        return wantedItems.get(toUnnotedId(id));
    }

    /**
     * Determines whether this bot currently wants the specified item.
     *
     * @param id The item id.
     * @return {@code true} if a wanted-item definition exists.
     */
    public boolean hasWantedItem(int id) {
        return wantedItems.containsKey(toUnnotedId(id));
    }

    /**
     * Raises the desired total-ownership target for a wanted item.
     * <p>
     * Existing metadata such as minimum amount, associated skill, and maximum level is preserved. The target is never
     * lowered by this method.
     *
     * @param id The item id.
     * @param target The new desired ownership target.
     */
    public void raiseWantedItemTarget(int id, int target) {
        int unnotedId = toUnnotedId(id);
        WantedItemDefinition current = wantedItems.get(unnotedId);

        if (current == null) {
            // No existing definition means this is a new temporary wanted item.
            addWantedItem(unnotedId, target);
            return;
        }

        if (target > current.target()) {
            // Preserve the persistent/temporary status and every other piece of wanted-item metadata.
            wantedItems.put(unnotedId, new WantedItemDefinition(
                    current.id(),
                    current.min(),
                    target,
                    current.skill(),
                    current.maxLevel(),
                    current.priority(),
                    current.tags()));
        }
    }

    /**
     * Completely removes a wanted-item definition.
     *
     * @param id The item id.
     * @return {@code true} if a wanted-item definition was removed.
     */
    public boolean removeWantedItem(int id) {
        return wantedItems.remove(toUnnotedId(id)) != null;
    }

    /**
     * Converts a noted item id to its unnoted id, so noted and unnoted copies count as the same wanted item.
     *
     * @param id The item id.
     * @return The unnoted id, or {@code id} itself if it isn't a noted item.
     */
    private static int toUnnotedId(int id) {
        if (!ItemDefinition.isIdValid(id)) {
            return id;
        }
        return ItemDefinition.ALL.get(id)
                .map(def -> def.getUnnotedId().orElse(id))
                .orElse(id);
    }

    /**
     * Gets this bot's current feeling value toward a player.
     * <p>
     * Unknown players default to {@code 0.5}, which represents neutral feelings.
     *
     * @param username The username of the player to check.
     * @return The bot's feeling value toward the player, from {@code 0.0} to {@code 1.0}.
     */
    public double getFeelingsToward(String username) {
        return playerFeelings.getOrDefault(username, 0.5);
    }

    /**
     * Adjusts this bot's feeling value toward a player.
     * <p>
     * Positive amounts make the bot like or trust the player more, while negative amounts make the bot dislike or distrust
     * the player more.
     *
     * @param username The username of the player to adjust feelings toward.
     * @param amount The amount to add to the player's current feeling value.
     */
    public void adjustFeelingsToward(String username, double amount) {
        playerFeelings.compute(username, (k, v) -> {
            double current = v == null ? 0.5 : v;
            return Math.max(0.0, Math.min(1.0, current + amount));
        });
    }

    /**
     * Determines if this bot strongly hates a player.
     *
     * @param username The username of the player to check.
     * @return {@code true} if this bot's feeling value toward the player is below {@code 0.20}.
     */
    public boolean hatesPlayer(String username) {
        return getFeelingsToward(username) < 0.20;
    }

    /**
     * Determines if this bot dislikes a player.
     *
     * @param username The username of the player to check.
     * @return {@code true} if this bot's feeling value toward the player is below {@code 0.40}.
     */
    public boolean dislikesPlayer(String username) {
        return getFeelingsToward(username) < 0.40;
    }

    /**
     * Determines if this bot feels neutral toward a player.
     *
     * @param username The username of the player to check.
     * @return {@code true} if this bot's feeling value toward the player is between {@code 0.40} and {@code 0.60}.
     */
    public boolean isNeutralToPlayer(String username) {
        double feelings = getFeelingsToward(username);
        return feelings >= 0.40 && feelings <= 0.60;
    }

    /**
     * Determines if this bot likes a player.
     *
     * @param username The username of the player to check.
     * @return {@code true} if this bot's feeling value toward the player is above {@code 0.60}.
     */
    public boolean likesPlayer(String username) {
        return getFeelingsToward(username) > 0.60;
    }

    /**
     * Determines if this bot strongly likes or trusts a player.
     *
     * @param username The username of the player to check.
     * @return {@code true} if this bot's feeling value toward the player is above {@code 0.80}.
     */
    public boolean lovesPlayer(String username) {
        return getFeelingsToward(username) > 0.80;
    }

    /**
     * Determines whether this bot strongly prefers the specified activity.
     *
     * @param activity The activity to check.
     * @return {@code true} if the activity's preference weight is greater than {@code 0.75}.
     */
    public boolean lovesActivity(BotActivity activity) {
        return activities.getOrDefault(activity, 0.0) > 0.75;
    }

    /**
     * Determines whether this bot likes the specified activity.
     *
     * @param activity The activity to check.
     * @return {@code true} if the activity's preference weight is greater than {@code 0.60}.
     */
    public boolean likesActivity(BotActivity activity) {
        return activities.getOrDefault(activity, 0.0) > 0.60;
    }

    /**
     * Determines whether this bot strongly dislikes the specified activity.
     *
     * @param activity The activity to check.
     * @return {@code true} if the activity's preference weight is less than {@code 0.20}.
     */
    public boolean hatesActivity(BotActivity activity) {
        return activities.getOrDefault(activity, 0.0) < 0.20;
    }

    /**
     * @return The immutable map of activity preferences.
     */
    public Map<BotActivity, Double> getActivities() {
        return activities;
    }

    /**
     * @return The immutable set of skill preferences.
     */
    public Set<Integer> getSkills() {
        return skills;
    }

    /**
     * Returns an iterator over this bot's current wanted-item definitions.
     *
     * @return An unmodifiable iterator over the wanted items.
     */
    public UnmodifiableIterator<WantedItemDefinition> getWantedItems() {
        return Iterators.unmodifiableIterator(wantedItems.values().iterator());
    }

    /**
     * @return The immutable set of gear preferences.
     */
    public Set<BotGearSet> getGear() {
        return gear;
    }

    /**
     * @return The mutable map of player feeling values by username.
     */
    public Map<String, Double> getPlayerFeelings() {
        return playerFeelings;
    }


    public Bot getBot() {
        return bot;
    }

    public void setBot(Bot bot) {
        this.bot = bot;
    }

}
