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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static api.predef.ext.BotPreferenceExtKt.totalItemAmount;
import static io.luna.game.model.def.WantedItemDefinition.DEFAULT;
import static io.luna.game.model.def.WantedItemDefinition.DEFAULT_TAGS;
import static io.luna.util.RandomUtils.randomFrom;
import static io.luna.util.RandomUtils.roll;

/**
 * Stores a bot's activity preferences, preferred skills and gear, wanted items, and player feelings.
 * <p>
 * Activities, skills, and gear may be generated from personality traits. Wanted items and
 * player feelings can change at runtime and are included in the saved preference profile.
 *
 * @author lare96
 */
public final class BotPreference {

    /**
     * Builds a {@link BotPreference} using explicit preferences or personality-based generation.
     * <p>
     * Activity weights can be copied from templates or randomized. Skills and gear are generated
     * from the supplied personality when not explicitly configured.
     */
    public static final class Builder {
        private final BotPersonality personality;
        private final BotPersonalityManager personalityManager;
        private final Map<BotActivity, Double> activities = new HashMap<>();
        private final Set<Integer> skills = new HashSet<>();
        private final Map<Integer, WantedItemDefinition> wantedItems = new HashMap<>();
        private final Set<BotGearSet> gear = new HashSet<>();
        private final Map<String, Double> playerFeelings = new HashMap<>();

        private BotPkPreference pk;

        /**
         * Supplies a permanent PK identity for explicitly configured bot builds.
         */
        public Builder setPk(BotPkPreference pk) {
            this.pk = java.util.Objects.requireNonNull(pk);
            return this;
        }

        private final double intelligence;
        private final double kindness;
        private final double confidence;
        private final double social;
        private final double dexterity;

        /**
         * Creates a preference builder using a personality and template manager.
         *
         * @param personalityManager The manager used to retrieve activity templates.
         * @param personality The personality used to generate skills and gear.
         */
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
         * Sets an activity preference without clamping the supplied weight.
         *
         * @param activity The activity to configure.
         * @param value The weight to store for the activity.
         * @return This builder for chaining.
         */
        public Builder setActivity(BotActivity activity, double value) {
            activities.put(activity, value);
            return this;
        }

        /**
         * Adds preferred skill IDs, ignoring duplicates.
         *
         * @param skillIds The skill IDs to add.
         * @return This builder for chaining.
         */
        public Builder addSkill(int... skillIds) {
            for (int id : skillIds) {
                skills.add(id);
            }
            return this;
        }

        /**
         * Adds preferred gear sets, ignoring duplicates.
         *
         * @param gearSets The gear sets to add.
         * @return This builder for chaining.
         */
        public Builder addGear(BotGearSet... gearSets) {
            gear.addAll(Arrays.asList(gearSets));
            return this;
        }

        /**
         * Adds or replaces a wanted-item definition keyed by its item ID.
         *
         * @param item The definition to add or replace.
         * @return This builder for chaining.
         */
        public Builder addWantedItem(WantedItemDefinition item) {
            wantedItems.put(item.id(), item);
            return this;
        }

        /**
         * Sets a player's initial feeling, clamped to {@code 0.0..1.0}.
         *
         * @param username The player's username.
         * @param feeling The feeling value to store.
         * @return This builder for chaining.
         */
        public Builder setFeelingsFor(String username, double feeling) {
            playerFeelings.put(username, Math.min(1.0, Math.max(feeling, 0.0)));
            return this;
        }

        /**
         * Copies activity weights from a predefined personality template.
         * <p>
         * Existing weights for matching activities are overwritten without random variance.
         *
         * @param from The template to load.
         * @return This builder for chaining.
         */
        public Builder template(PersonalityTemplateType from) {
            PersonalityTemplate template = personalityManager.getTemplate(from);
            activities.putAll(template.activities);
            return this;
        }


        /**
         * Randomizes activity weights around a randomly chosen personality template.
         * <p>
         * Chooses a variance magnitude from {@code 0.05..0.25}, applies an independent adjustment
         * to each template weight, and clamps the result to {@code 0.0..1.0}.
         *
         * @return This builder for chaining.
         */
        public Builder randomizeSmart() {
            double variance = ThreadLocalRandom.current().nextDouble(0.05, 0.25);
            Supplier<Double> varianceSupplier = () -> ThreadLocalRandom.current().nextDouble(-variance, variance);
            PersonalityTemplate template = personalityManager.getTemplate(RandomUtils.random(PersonalityTemplateType.ALL_EXCEPT_NONE));
            for (var entry : template.activities.entrySet()) {
                double weight = entry.getValue() + varianceSupplier.get();
                activities.put(entry.getKey(), Math.clamp(weight, 0.0, 1.0));
            }
            return this;
        }


        /**
         * Assigns a random weight to each activity in {@link BotActivity#ALL}.
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
         * Generates preferred skills from the personality unless explicit skills were added.
         * <p>
         * Personality traits may select overlapping skill IDs. If none are selected, two to four
         * IDs are drawn without replacement from {@link Skill#IDS}.
         *
         * @return A mutable set of preferred skill IDs.
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

                // Fallback: pick 2–4 random skills
                if (selected.isEmpty()) {
                    int max = RandomUtils.inclusive(2, 4);
                    List<Integer> skills = new ArrayList<>(Skill.IDS);
                    Collections.shuffle(skills);
                    while (max-- > 0) {
                        Integer id = skills.removeFirst();
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
         * Generates preferred gear from personality traits unless explicit gear was added.
         * <p>
         * Confidence and kindness influence combat gear, intelligence favors magical gear,
         * sociability favors decorative sets, and dexterity favors light or ranged equipment.
         * A fallback set is selected if none of these traits produces gear.
         *
         * @return A mutable set of preferred gear sets.
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
         * Builds a preference profile from the current builder state.
         * <p>
         * Preferred skills and gear are generated only when their builder sets are empty.
         *
         * @return A new {@link BotPreference} instance.
         */
        public BotPreference build() {
            boolean skiller = personality.getType() == PersonalityTemplateType.SKILLER;
            if (skiller) {
                activities.put(BotActivity.PKING, 0.0);
                activities.put(BotActivity.TRAINING_COMBAT, 0.0);
                activities.put(BotActivity.PROFIT_COMBAT, 0.0);
            }
            BotPreference preference = new BotPreference(
                    new HashMap<>(activities),
                    new HashSet<>(generateSkills()),
                    new HashMap<>(wantedItems),
                    new HashSet<>(generateGear()),
                    new HashMap<>(playerFeelings));
            preference.pk = skiller ? null : pk != null ? pk :
                    personality.getType() == PersonalityTemplateType.PKER ||
                    preference.likesActivity(BotActivity.PKING) ? BotPkPreference.generate(null) : null;
            if (skiller) preference.skills.removeIf(Skill::isCombatSkill);
            else if (preference.pk != null) preference.skills.removeIf(id ->
                    Skill.isCombatSkill(id) && preference.pk.build().targetLevel(id) == 1);
            return preference;
        }
    }

    /**
     * The bot associated with this preference profile, if one has been assigned.
     */
    private Bot bot;

    /**
     * Permanent PK purpose, style combination and build; absent for bots without a PK preference.
     * Saved identities replace the builder's provisional identity when an account is restored.
     */
    private BotPkPreference pk;

    /**
     * Returns the permanent PK identity, or {@code null} when PKing is not desired.
     */
    public BotPkPreference getPk() {
        return pk;
    }

    /**
     * Applies the skiller invariant even when activity weights were edited directly or loaded from old saves.
     */
    public boolean canChooseActivity(BotActivity activity) {
        return !isSkiller() || (activity != BotActivity.PKING &&
                activity != BotActivity.TRAINING_COMBAT && activity != BotActivity.PROFIT_COMBAT);
    }

    /**
     * Normalizes a bound skiller after construction or save restoration.
     */
    private void enforceSkiller() {
        if (isSkiller()) {
            pk = null;
            activities.put(BotActivity.PKING, 0.0);
            activities.put(BotActivity.TRAINING_COMBAT, 0.0);
            activities.put(BotActivity.PROFIT_COMBAT, 0.0);
            skills.removeIf(id -> Skill.isCombatSkill(id));
        }
    }

    /**
     * The mutable activity preference weights, keyed by activity.
     */
    private final Map<BotActivity, Double> activities;

    /**
     * The mutable set of preferred skill ids.
     */
    private final Set<Integer> skills;

    /**
     * The mutable wanted-item definitions, keyed by item ID.
     * <p>
     * Missing default definitions are inserted during construction. Definitions can also be
     * added, replaced, or have their targets increased at runtime.
     */
    private final Map<Integer, WantedItemDefinition> wantedItems;

    /**
     * The mutable set of preferred gear archetypes.
     */
    private final Set<BotGearSet> gear;

    /**
     * The mutable feelings toward players, keyed by username.
     * <p>
     * A value of {@code 0.0} represents strong dislike, {@code 0.5} neutrality, and {@code 1.0}
     * strong approval. Unknown players are treated as neutral by {@link #getFeelingsToward(String)}.
     */
    private final Map<String, Double> playerFeelings;

    /**
     * Creates a preference profile from the supplied collections.
     * <p>
     * Inserts the default general-activity weight and missing default wanted items into the
     * supplied maps. Activity weights are copied; the other collections are retained by reference.
     *
     * @param activities The activity weights, keyed by activity.
     * @param skills The preferred skill IDs.
     * @param wantedItems The wanted-item definitions, keyed by item ID.
     * @param gear The preferred gear sets.
     * @param playerFeelings The player feelings, keyed by username.
     */
    public BotPreference(Map<BotActivity, Double> activities,
                         Set<Integer> skills,
                         Map<Integer, WantedItemDefinition> wantedItems,
                         Set<BotGearSet> gear,
                         Map<String, Double> playerFeelings) {

        // New and legacy profiles participate in general activities unless an explicit weight is provided.
        activities.putIfAbsent(BotActivity.GENERAL_ACTIVITIES, 0.25);

        // Load default wanted items.
        for (WantedItemDefinition def : WantedItemDefinition.DEFAULT) {
            wantedItems.putIfAbsent(def.id(), def);
        }

        this.activities = new HashMap<>(activities);
        this.skills = skills;
        this.wantedItems = wantedItems;
        this.gear = gear;
        this.playerFeelings = playerFeelings;
    }

    /**
     * Serializes this preference profile into JSON.
     * <p>
     * Saves activity weights, preferred skills and gear, wanted-item definitions with their
     * priority and tags, and player feelings. The associated bot is not serialized.
     *
     * @return The JSON representation of this preference profile.
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
            itemJson.addProperty("priority", it.priority().name());
            itemJson.add("tags", GsonUtils.toJsonTree(it.tags().stream().map(WantedItemTag::name).toList()));
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
        preferences.add("pk", pk == null ? com.google.gson.JsonNull.INSTANCE : pk.save());
        return preferences;
    }

    /**
     * Loads saved preferences into the existing collections.
     * <p>
     * Saved entries are merged without clearing current values. Wanted items lacking a minimum
     * field are skipped; missing priority or tags use their respective default values.
     *
     * @param object The JSON object containing saved preferences.
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
                WantedItemPriority priority = !itemJson.has("priority") ? WantedItemPriority.STANDARD :
                        WantedItemPriority.valueOf(itemJson.get("priority").getAsString());
                ImmutableSet<WantedItemTag> tags = !itemJson.has("tags") ? DEFAULT_TAGS :
                        ImmutableSet.copyOf(GsonUtils.getAsType(itemJson.get("tags"), WantedItemTag[].class));
                wantedItems.put(id, new WantedItemDefinition(id, min, target, skill, maxLevel, priority, tags));
            }
        });
        object.getAsJsonArray("gear").forEach(it -> gear.add(BotGearSet.valueOf(it.getAsString())));
        object.getAsJsonArray("player_feelings").forEach(it -> {
            JsonObject feelingsJson = it.getAsJsonObject();
            playerFeelings.put(feelingsJson.get("username").getAsString(), feelingsJson.get("feeling").getAsDouble());
        });
        // load() runs after PlayerData restores the actual personality and skills.
        if (object.has("pk")) {
            pk = object.get("pk").isJsonNull() ? null : BotPkPreference.load(object.getAsJsonObject("pk"));
        } else {
            pk = !isSkiller() && (isPker() || likesActivity(BotActivity.PKING)) ?
                    BotPkPreference.generate(bot) : null;
        }
        enforceSkiller();
    }

    /**
     * Adds or replaces a wanted item after normalizing its ID to the unnoted form.
     * <p>
     * Sets its minimum to {@code -1}. Preserves an existing priority and tags; otherwise uses
     * high priority and tags from the default definition, or the default tag set if absent.
     *
     * @param id The item ID to add or replace.
     * @param target The desired total ownership quantity.
     * @param skill The associated skill ID, or {@code -1} if unrestricted.
     * @param maxLevel The maximum relevant skill level, or {@code -1} if unrestricted.
     */
    public void addWantedItem(int id, int target, int skill, int maxLevel) {
        int unnotedId = toUnnotedId(id);
        WantedItemDefinition existing = wantedItems.get(unnotedId);
        var tags = existing != null ? existing.tags() :
                WantedItemDefinition.DEFAULT.get(unnotedId).map(WantedItemDefinition::tags).orElse(DEFAULT_TAGS);
        wantedItems.put(unnotedId, new WantedItemDefinition(unnotedId, -1, target, skill, maxLevel, existing != null ? existing.priority() : WantedItemPriority.HIGH, tags));
    }

    /**
     * Adds or replaces a wanted item without skill or level restrictions.
     *
     * @param id The item id to add or replace.
     * @param target The desired item quantity.
     */
    public void addWantedItem(int id, int target) {
        addWantedItem(id, target, -1, -1);
    }

    /**
     * Gets a wanted-item definition after normalizing the item id.
     *
     * @param id The item id to look up.
     * @return The wanted-item definition, or {@code null} if it does not exist.
     */
    public WantedItemDefinition getWantedItem(int id) {
        return wantedItems.get(toUnnotedId(id));
    }

    /**
     * Checks whether a wanted-item definition exists for an item id.
     *
     * @param id The item id to check, normalized before lookup.
     * @return {@code true} if a definition is present.
     */
    public boolean hasWantedItem(int id) {
        return wantedItems.containsKey(toUnnotedId(id));
    }

    /**
     * Checks whether a wanted item exists and the bot owns less than its target.
     *
     * @param id The item id to check, normalized before lookup.
     * @return {@code true} if the bot still needs more of the wanted item.
     */
    public boolean needsWantedItem(int id) {
        WantedItemDefinition wanted = getWantedItem(id);
        return bot != null
                && wanted != null
                && totalItemAmount(bot, wanted.id()) < wanted.target();
    }

    /**
     * Raises a wanted-item target without decreasing an existing target.
     * <p>
     * Creates an unrestricted wanted item if none exists. Otherwise, preserves all existing
     * definition metadata apart from the target quantity.
     *
     * @param id The item ID, normalized before lookup.
     * @param target The proposed total ownership target.
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
     * Removes a wanted-item definition unless its ID belongs to the default definitions.
     * <p>
     * A default item cannot be removed even if its definition has been replaced or modified.
     *
     * @param id The item ID, normalized before lookup.
     * @return {@code true} if a nondefault definition was removed.
     */
    public boolean removeWantedItem(int id) {
        int unnotedId = toUnnotedId(id);
        if (DEFAULT.get(unnotedId).isEmpty()) {
            return wantedItems.remove(unnotedId) != null;
        }
        return false;
    }

    /**
     * Resolves a noted item id to its unnoted counterpart when available.
     *
     * @param id The item id to normalize.
     * @return The unnoted id, or the original id when no conversion is available.
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
     * Gets the stored feeling toward a player, defaulting to {@code 0.5}.
     *
     * @param username The player's username.
     * @return The stored feeling value, or {@code 0.5} if no entry exists.
     */
    public double getFeelingsToward(String username) {
        return playerFeelings.getOrDefault(username, 0.5);
    }

    /**
     * Adjusts the feeling toward a player, clamping the result to {@code 0.0..1.0}.
     * <p>
     * Players without a stored feeling begin at {@code 0.5}.
     *
     * @param username The player's username.
     * @param amount The amount to add to the existing feeling.
     */
    public void adjustFeelingsToward(String username, double amount) {
        playerFeelings.compute(username, (k, v) -> {
            double current = v == null ? 0.5 : v;
            return Math.max(0.0, Math.min(1.0, current + amount));
        });
    }

    /**
     * Checks whether the bot's feeling toward a player is below {@code 0.20}.
     *
     * @param username The player's username.
     * @return {@code true} if the feeling is below {@code 0.20}.
     */
    public boolean hatesPlayer(String username) {
        return getFeelingsToward(username) < 0.20;
    }

    /**
     * Checks whether the bot's feeling toward a player is below {@code 0.40}.
     *
     * @param username The player's username.
     * @return {@code true} if the feeling is below {@code 0.40}.
     */
    public boolean dislikesPlayer(String username) {
        return getFeelingsToward(username) < 0.40;
    }

    /**
     * Checks whether the bot's feeling is between {@code 0.40} and {@code 0.60}, inclusive.
     *
     * @param username The player's username.
     * @return {@code true} if the feeling is within the neutral range.
     */
    public boolean isNeutralToPlayer(String username) {
        double feelings = getFeelingsToward(username);
        return feelings >= 0.40 && feelings <= 0.60;
    }

    /**
     * Checks whether the bot's feeling toward a player exceeds {@code 0.60}.
     *
     * @param username The player's username.
     * @return {@code true} if the feeling is above {@code 0.60}.
     */
    public boolean likesPlayer(String username) {
        return getFeelingsToward(username) > 0.60;
    }

    /**
     * Checks whether the bot's feeling toward a player exceeds {@code 0.80}.
     *
     * @param username The player's username.
     * @return {@code true} if the feeling is above {@code 0.80}.
     */
    public boolean lovesPlayer(String username) {
        return getFeelingsToward(username) > 0.80;
    }

    /**
     * Checks whether an activity weight exceeds {@code 0.75}.
     *
     * @param activity The activity to check.
     * @return {@code true} if the stored weight is above {@code 0.75}.
     */
    public boolean lovesActivity(BotActivity activity) {
        return canChooseActivity(activity) && activities.getOrDefault(activity, 0.0) > 0.75;
    }

    /**
     * Checks whether an activity weight exceeds {@code 0.60}.
     *
     * @param activity The activity to check.
     * @return {@code true} if the stored weight is above {@code 0.60}.
     */
    public boolean likesActivity(BotActivity activity) {
        return canChooseActivity(activity) && activities.getOrDefault(activity, 0.0) > 0.60;
    }

    /**
     * Checks whether an activity weight is below {@code 0.20}.
     * <p>
     * Activities without a stored weight are treated as having weight {@code 0.0}.
     *
     * @param activity The activity to check.
     * @return {@code true} if the stored or default weight is below {@code 0.20}.
     */
    public boolean hatesActivity(BotActivity activity) {
        return activities.getOrDefault(activity, 0.0) < 0.20;
    }

    /**
     * Checks whether the associated bot has the specified personality template.
     *
     * @param type The template type to check.
     * @return {@code true} if an associated bot has a matching personality template.
     */
    private boolean hasPersonalityTemplate(PersonalityTemplateType type) {
        return bot != null && bot.getPersonality() != null && bot.getPersonality().getType() == type;
    }

    /**
     * Determines whether the bot qualifies as a skiller.
     * <p>
     * The skiller personality always takes precedence over randomized or saved combat weights.
     *
     * @return {@code true} if the bot has the skiller template.
     */
    public boolean isSkiller() {
        return hasPersonalityTemplate(PersonalityTemplateType.SKILLER);
    }

    /**
     * Determines whether the bot uses the PKer personality template.
     * <p>
     * PKing activity preferences alone do not qualify a bot as a PKer.
     *
     * @return {@code true} if the bot uses the {@link PersonalityTemplateType#PKER} template.
     */
    public boolean isPker() {
        return hasPersonalityTemplate(PersonalityTemplateType.PKER);
    }

    /**
     * Determines whether the bot qualifies as a merchant.
     * <p>
     * Matches the {@link PersonalityTemplateType#MERCHANT} template or a merchanting activity
     * weight above {@code 0.60}. Bots with other templates may also qualify.
     *
     * @return {@code true} if the bot has the merchant template or likes merchanting.
     */
    public boolean isMerchant() {
        return hasPersonalityTemplate(PersonalityTemplateType.MERCHANT) || lovesActivity(BotActivity.MERCHANTING);
    }

    /**
     * Determines whether the bot favors combat training or profitable combat.
     * <p>
     * Requires combat-training weight above {@code 0.75}, or both combat-training and profitable
     * combat weights above {@code 0.60}. PKing is classified separately by {@link #isPker()}.
     *
     * @return {@code true} if the bot meets either combat preference condition.
     */
    public boolean isCombatant() {
        return lovesActivity(BotActivity.TRAINING_COMBAT) ||
                (likesActivity(BotActivity.TRAINING_COMBAT) && likesActivity(BotActivity.PROFIT_COMBAT));
    }

    /**
     * Determines whether the bot qualifies as a socializer.
     * <p>
     * Matches the {@link PersonalityTemplateType#HERO} template or a socializing activity
     * weight above {@code 0.60}.
     *
     * @return {@code true} if the bot has the hero template or likes socializing.
     */
    public boolean isSocializer() {
        return hasPersonalityTemplate(PersonalityTemplateType.HERO) || likesActivity(BotActivity.SOCIALIZING);
    }

    /**
     * Determines whether the bot likes participating in minigames.
     *
     * @return {@code true} if the minigames activity weight exceeds {@code 0.60}.
     */
    public boolean isMinigamer() {
        return likesActivity(BotActivity.MINIGAMES);
    }

    /**
     * Checks whether the bot uses the no-life personality template.
     *
     * @return {@code true} if the bot uses {@link PersonalityTemplateType#NO_LIFE}.
     */
    public boolean isNoLife() {
        return hasPersonalityTemplate(PersonalityTemplateType.NO_LIFE);
    }

    /**
     * Checks whether the bot uses the kid personality template.
     *
     * @return {@code true} if the bot uses {@link PersonalityTemplateType#KID}.
     */
    public boolean isKid() {
        return hasPersonalityTemplate(PersonalityTemplateType.KID);
    }

    /**
     * Checks whether the bot uses the hero personality template.
     *
     * @return {@code true} if the bot uses {@link PersonalityTemplateType#HERO}.
     */
    public boolean isHero() {
        return hasPersonalityTemplate(PersonalityTemplateType.HERO);
    }

    /**
     * Checks whether the bot uses the scammer personality template.
     *
     * @return {@code true} if the bot uses {@link PersonalityTemplateType#SCAMMER}.
     */
    public boolean isScammer() {
        return hasPersonalityTemplate(PersonalityTemplateType.SCAMMER);
    }

    /**
     * Checks whether the bot uses the jack-of-all-trades personality template.
     *
     * @return {@code true} if the bot uses {@link PersonalityTemplateType#JACK_OF_ALL_TRADES}.
     */
    public boolean isJackOfAllTrades() {
        return hasPersonalityTemplate(PersonalityTemplateType.JACK_OF_ALL_TRADES);
    }

    /**
     * Gets the mutable activity preference map.
     *
     * @return The activity weights keyed by activity.
     */
    public Map<BotActivity, Double> getActivities() {
        return activities;
    }

    /**
     * Gets the mutable set of preferred skill ids.
     *
     * @return The preferred skill ids.
     */
    public Set<Integer> getSkills() {
        return skills;
    }

    /**
     * Gets an unmodifiable iterator over the current wanted-item definitions.
     * <p>
     * The iterator is backed by the underlying mutable wanted-item map.
     *
     * @return An unmodifiable iterator over wanted-item definitions.
     */
    public UnmodifiableIterator<WantedItemDefinition> getWantedItems() {
        return Iterators.unmodifiableIterator(wantedItems.values().iterator());
    }

    /**
     * Gets the mutable set of preferred gear sets.
     *
     * @return The preferred gear sets.
     */
    public Set<BotGearSet> getGear() {
        return gear;
    }

    /**
     * Gets the mutable player-feeling map.
     *
     * @return The player feeling values keyed by username.
     */
    public Map<String, Double> getPlayerFeelings() {
        return playerFeelings;
    }


    /**
     * Gets the bot associated with this preference profile.
     *
     * @return The associated bot, or {@code null} if none has been assigned.
     */
    public Bot getBot() {
        return bot;
    }

    /**
     * Sets the bot associated with this preference profile.
     *
     * @param bot The bot to associate with this profile.
     */
    public void setBot(Bot bot) {
        this.bot = bot;
        enforceSkiller();
    }
}
