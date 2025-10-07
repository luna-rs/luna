package io.luna.game.model.mob.bot.brain;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.GearSetType;
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplate;
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType;
import io.luna.util.RandomUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static io.luna.util.RandomUtils.randomFrom;

/**
 * Represents a {@link Bot}s behavioral preferences.
 * <p>
 * Preferences describe what a bot enjoys doing (activities), what it likes to train (skills), what items it
 * values (regardless of market value), and what equipment it tends to wear. These are influenced by the bot’s
 * {@link BotPersonality} and evolve dynamically at runtime through its {@link BotEmotion} states.
 *
 * <p>For example:
 * <ul>
 *   <li>A highly intelligent bot may enjoy resource-based skills like Crafting and Herblore.</li>
 *   <li>A confident and low-kindness bot might favor combat or PvP-related activities.</li>
 *   <li>Social bots tend to prefer group-oriented skills and activities like Fishing or Firemaking.</li>
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
        private final ImmutableMap.Builder<BotActivity, Double> activities = ImmutableMap.builder();
        private final Set<Integer> skills = new HashSet<>();
        private final Set<Integer> items = new HashSet<>();
        private final Set<GearSetType> gear = new HashSet<>();

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
         * Adds one or more item identifiers to this bot’s preferred item collection.
         * <p>
         * This can be used to directly seed item preferences for trade, hoarding, or usage.
         * Items added here will be considered inherently valuable or desirable to the bot.
         *
         * @param itemIds One or more item IDs to mark as preferred.
         * @return This builder for chaining.
         */
        public Builder addItem(int... itemIds) {
            for (int id : itemIds) {
                items.add(id);
            }
            return this;
        }

        /**
         * Adds one or more {@link GearSetType} values to this bot’s preferred equipment list.
         * <p>
         * This allows manual assignment of specific armor or outfit sets that the bot will attempt to equip
         * or seek when generating loadouts.
         *
         * @param gearSets One or more gear set types to add as preferences.
         * @return This builder for chaining.
         */
        public Builder addGear(GearSetType... gearSets) {
            gear.addAll(Arrays.asList(gearSets));
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
         * Loads activity preferences from a predefined {@link PersonalityTemplateType} and applies random variance.
         * <p>
         * Variance introduces natural deviation, making bots feel more individualistic while still aligned to
         * a general archetype.
         *
         * @param from The base template type.
         * @param variance The maximum allowed deviation (e.g. {@code 0.15} for ±15%).
         * @return This builder for chaining.
         */
        public Builder randomizeTemplate(PersonalityTemplateType from, double variance) {
            Supplier<Double> varianceSupplier = () -> ThreadLocalRandom.current().nextDouble(-variance, variance);
            PersonalityTemplate template = personalityManager.getTemplate(from);
            for (var entry : template.activities.entrySet()) {
                activities.put(entry.getKey(), entry.getValue() + varianceSupplier.get());
            }
            return this;
        }

        /**
         * Randomizes this bot’s preferences using a random template type and a dynamic variance value.
         * <p>
         * The variance is chosen randomly between {@code 5.0} and {@code 25.0}, introducing a natural spread of
         * unique preferences without producing extreme outliers.
         *
         * @return This builder instance for chaining.
         */
        public Builder randomizeSmart() {
            for (BotActivity activity : BotActivity.ALL) {
                activities.put(activity, RandomUtils.nextDouble());
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
        private ImmutableSet<Integer> generateSkills() {
            ImmutableSet.Builder<Integer> selected = ImmutableSet.builder();
            if (personality.isIntelligent()) {
                selected.add(Skill.MAGIC, Skill.FLETCHING, Skill.CRAFTING, Skill.FARMING, Skill.RUNECRAFTING,
                        Skill.HERBLORE);
            }
            if (personality.isConfident()) {
                selected.add(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE,
                        Skill.HITPOINTS, Skill.SLAYER, Skill.RANGED);
            }
            if (personality.isSocial()) {
                selected.add(Skill.FISHING, Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING, Skill.AGILITY);
            }
            if (personality.isDextrous()) {
                selected.add(Skill.FLETCHING, Skill.RANGED, Skill.AGILITY, Skill.CRAFTING,
                        Skill.SMITHING, Skill.WOODCUTTING, Skill.MINING);
            }
            if (personality.isMean()) {
                selected.add(Skill.THIEVING);
            } else if (personality.isKind()) {
                selected.add(Skill.PRAYER);
            }
            selected.addAll(skills);
            ImmutableSet<Integer> generated = selected.build();
            if (generated.isEmpty()) {
                // Fallback: pick 2–6 random skills
                for (int loops = 0; loops < RandomUtils.inclusive(2, 6); loops++) {
                    Integer id = RandomUtils.random(Skill.IDS);
                    if (id != null) {
                        selected.add(id);
                    }
                }
                return selected.build();
            }
            return generated;
        }

        /**
         * Generates a set of item preferences representing what types of items this bot values or seeks.
         *
         * @return A generated immutable set of preferred item IDs.
         */
        private ImmutableSet<Integer> generateItems() {
            ImmutableSet.Builder<Integer> selected = ImmutableSet.builder();
            // TODO In tagging system, track how often items are moving and being obtained by bots (can see if an item
            // needs a sink, to be introduced somewhere new, or reduced).
            if (personality.isIntelligent()) {
                // TODO Smart bots prefer useful or valuable items (e.g. resources, consumables, rares, etc.)
                // TODO Some sort of item tagging system for the bot economy?
            } else if (personality.isDumb()) {
                // TODO Dumb bots pick random junk (random items from the tagging system with value < 1000?)
            }
            if (personality.isConfident()) {
                // TODO They chase rares, the most valuable items in the tagging system, show-off items.
            }
            if (personality.isDextrous()) {
                // TODO Love resources
            }
            selected.addAll(items);
            // TODO if empty, pick 5 random items from tagging system?
            return selected.build();
        }

        /**
         * Generates a set of preferred gear archetypes based on the bot’s personality traits.
         * <p>
         * Gear preferences are chosen by interpreting the bot’s combat style tendencies and self-image.
         * For example:
         * <ul>
         *   <li>Highly confident, low-kindness bots tend to favor offensive or high-status gear (e.g. {@code DRAGON_FULL}).</li>
         *   <li>Intelligent bots prefer efficient or magically oriented sets (e.g. {@code MYSTIC_LIGHT_SET}, {@code AHRIMS_SET}).</li>
         *   <li>Social bots often choose visually striking “show-off” sets.</li>
         *   <li>Dexterous bots lean toward ranged or lightweight setups.</li>
         * </ul>
         *
         * @return A generated immutable set of {@link GearSetType}s representing this bot’s favored loadouts.
         */
        private ImmutableSet<GearSetType> generateGear() {
            ImmutableSet.Builder<GearSetType> selected = ImmutableSet.builder();

            if (personality.isConfident() && personality.isMean()) {
                if (RandomUtils.rollPercent(20)) {
                    selected.add(GearSetType.OBSIDIAN_FULL, GearSetType.GUTHANS, GearSetType.DHAROKS, GearSetType.VERACS);
                } else {
                    selected.add(GearSetType.DRAGON_FULL, GearSetType.AHRIMS_SET, GearSetType.KARILS_SET);
                }
                if (RandomUtils.rollPercent(10)) {
                    selected.add(randomFrom(GearSetType.DHAROKS, GearSetType.VERACS,
                            GearSetType.TORAGS, GearSetType.GUTHANS));
                }
            }

            if (personality.isIntelligent()) {
                if (RandomUtils.rollPercent(15)) {
                    selected.add(GearSetType.AHRIMS_SET);
                } else if (RandomUtils.rollPercent(40)) {
                    selected.add(GearSetType.MYSTIC_DARK_SET);
                } else {
                    selected.add(GearSetType.MYSTIC_LIGHT_SET);
                }

                // Occasionally add wizard sets for low-level intelligent bots.
                if (confidence < 0.5) {
                    selected.add(randomFrom(GearSetType.WIZARD_BLUE_SET, GearSetType.WIZARD_DARK_SET));
                }
            }

            // --- Social or aesthetic personalities (show-off) ---
            if (social >= 0.7) {
                selected.add(randomFrom(
                        GearSetType.RUNE_TRIMMED,
                        GearSetType.RUNE_GOLD,
                        GearSetType.RUNE_SARADOMIN,
                        GearSetType.RUNE_GUTHIX,
                        GearSetType.RUNE_ZAMORAK,
                        GearSetType.GILDED_RUNE,
                        GearSetType.BLACK_TRIMMED,
                        GearSetType.BLACK_GOLD,
                        GearSetType.MITHRIL_TRIMMED,
                        GearSetType.MITHRIL_GOLD,
                        GearSetType.ADAMANT_TRIMMED,
                        GearSetType.ADAMANT_GOLD));
            }

            // --- Dexterous bots: light, ranged-focused, practical gear ---
            if (dexterity >= 0.7) {
                if (RandomUtils.rollPercent(30)) {
                    selected.add(GearSetType.KARILS_SET);
                } else {
                    selected.add(randomFrom(
                            GearSetType.GREEN_DHIDE_SET,
                            GearSetType.BLUE_DHIDE_SET,
                            GearSetType.RED_DHIDE_SET,
                            GearSetType.BLACK_DHIDE_SET));
                }

                // Low-level dexterous bots might wear light armor sets.
                if (confidence < 0.5) {
                    selected.add(randomFrom(GearSetType.LEATHER_SET, GearSetType.STUDDED_LEATHER_SET));
                }
            }
            selected.addAll(gear);
            // --- Fallback: unremarkable or inexperienced personalities ---
            ImmutableSet<GearSetType> generated = selected.build();
            if (generated.isEmpty()) {
                selected.add(GearSetType.ADAMANT_FULL,
                        GearSetType.RUNE_FULL);
                selected.add(randomFrom(GearSetType.KARILS_SET, GearSetType.DRAGON_FULL, GearSetType.BLACK_FULL,
                        GearSetType.GUTHANS, GearSetType.DHAROKS));
                return generated;
            }
            return generated;
        }

        /**
         * Builds the resulting immutable {@link BotPreference} instance. Generates favorite skills, items, and gear
         * from the bot's personality.
         *
         * @return The fully built preference profile.
         */
        public BotPreference build() {
            return new BotPreference(
                    activities.build(),
                    generateSkills(),
                    generateItems(),
                    generateGear());
        }

    }

    /**
     * The immutable map of activity preference weights.
     */
    private final ImmutableMap<BotActivity, Double> activities;

    /**
     * The immutable set of preferred skills.
     */
    private final ImmutableSet<Integer> skills;

    /**
     * The immutable set of preferred item IDs.
     */
    private final ImmutableSet<Integer> items;

    /**
     * The immutable set of preferred gear archetypes.
     */
    private final ImmutableSet<GearSetType> gear;

    /**
     * Creates a new immutable {@link BotPreference}.
     *
     * @param activities Activity preference weights.
     * @param skills Preferred skill IDs.
     * @param items Preferred item IDs.
     * @param gear Preferred gear archetypes.
     */
    public BotPreference(ImmutableMap<BotActivity, Double> activities,
                         ImmutableSet<Integer> skills,
                         ImmutableSet<Integer> items,
                         ImmutableSet<GearSetType> gear) {
        this.activities = activities;
        this.skills = skills;
        this.items = items;
        this.gear = gear;
    }

    /**
     * @return The immutable map of activity preferences.
     */
    public ImmutableMap<BotActivity, Double> getActivities() {
        return activities;
    }

    /**
     * @return The immutable set of skill preferences.
     */
    public ImmutableSet<Integer> getSkills() {
        return skills;
    }

    /**
     * @return The immutable set of item preferences.
     */
    public ImmutableSet<Integer> getItems() {
        return items;
    }

    /**
     * @return The immutable set of gear preferences.
     */
    public ImmutableSet<GearSetType> getGear() {
        return gear;
    }
}