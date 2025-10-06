package io.luna.game.model.mob.bot.brain;

import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplate;
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType;
import io.luna.util.RandomUtils;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Represents a selection of traits that make up the personality of a {@link Bot}.
 * <p>
 * A bot's personality determines how robust its survival instincts are, how it navigates through the world,
 * and how it interacts with other bots and players.
 *
 * <p>Each bot is assigned a unique combination of core attributes
 * ({@code intelligence}, {@code kindness}, {@code confidence}, {@code social}, {@code dexterity}),
 * which collectively influence its decision-making, emotional responses, and behavioral quirks.
 *
 * <p>For example, a bot with high confidence and low kindness may appear arrogant, while a bot with
 * high intelligence and low confidence might behave cautiously or submissively.
 *
 * <p>This class also defines a nested {@link Builder} for generating new personality instances,
 * either from predefined templates or fully randomized distributions.
 *
 * @author lare96
 */
public final class BotPersonality {

    /**
     * A fluent builder used to construct {@link BotPersonality} instances.
     * <p>
     * Builders can load values from predefined {@link PersonalityTemplate}s, apply random variance,
     * or generate entirely randomized personalities for testing or procedural generation.
     */
    public static final class Builder {

        /**
         * The owning bot.
         */
        private final Bot bot;

        /**
         * Core personality traits. Defaulted to -1 to indicate unset state.
         */
        private double intelligence = -1;
        private double kindness = -1;
        private double confidence = -1;
        private double social = -1;
        private double dexterity = -1;

        /**
         * Creates a new builder for the specified bot.
         *
         * @param bot The bot this personality belongs to.
         */
        public Builder(Bot bot) {
            this.bot = bot;
        }

        /**
         * Sets the intelligence trait.
         *
         * @param intelligence The intelligence value between {@code 0.0} and {@code 1.0}.
         * @return This builder for chaining.
         */
        public Builder setIntelligence(double intelligence) {
            this.intelligence = intelligence;
            return this;
        }

        /**
         * Sets the kindness trait.
         *
         * @param kindness The kindness value between {@code 0.0} and {@code 1.0}.
         * @return This builder for chaining.
         */
        public Builder setKindness(double kindness) {
            this.kindness = kindness;
            return this;
        }

        /**
         * Sets the confidence trait.
         *
         * @param confidence The confidence value between {@code 0.0} and {@code 1.0}.
         * @return This builder for chaining.
         */
        public Builder setConfidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        /**
         * Sets the social trait.
         *
         * @param social The social value between {@code 0.0} and {@code 1.0}.
         * @return This builder for chaining.
         */
        public Builder setSocial(double social) {
            this.social = social;
            return this;
        }

        /**
         * Sets the dexterity trait.
         *
         * @param dexterity The dexterity value between {@code 0.0} and {@code 1.0}.
         * @return This builder for chaining.
         */
        public Builder setDexterity(double dexterity) {
            this.dexterity = dexterity;
            return this;
        }

        /**
         * Loads values from a predefined {@link PersonalityTemplateType} without applying any variance.
         *
         * @param from The template type to load from.
         * @return This builder for chaining.
         */
        public Builder template(PersonalityTemplateType from) {
            PersonalityTemplate template = bot.getManager().getPersonalityManager().getTemplate(from);
            intelligence = template.getIntelligence();
            kindness = template.getKindness();
            confidence = template.getConfidence();
            social = template.getSocial();
            dexterity = template.getDexterity();
            return this;
        }

        /**
         * Loads values from a predefined {@link PersonalityTemplateType} and applies random variance.
         * <p>
         * Variance introduces subtle deviations to create more organic and individualized behavior.
         *
         * @param from The base template type.
         * @param variance The maximum deviation for each trait.
         * @return This builder for chaining.
         */
        public Builder randomizeTemplates(PersonalityTemplateType from, double variance) {
            Supplier<Double> varianceSupplier = () -> ThreadLocalRandom.current().nextDouble(-variance, variance);
            PersonalityTemplate template = bot.getManager().getPersonalityManager().getTemplate(from);
            intelligence = template.getIntelligence() + varianceSupplier.get();
            kindness = template.getKindness() + varianceSupplier.get();
            confidence = template.getConfidence() + varianceSupplier.get();
            social = template.getSocial() + varianceSupplier.get();
            dexterity = template.getDexterity() + varianceSupplier.get();
            return this;
        }

        /**
         * Generates a fully random personality with all traits uniformly distributed
         * between {@code 0.0} and {@code 1.0}.
         *
         * @return This builder for chaining.
         */
        public Builder randomize() {
            intelligence = RandomUtils.nextDouble();
            kindness = RandomUtils.nextDouble();
            confidence = RandomUtils.nextDouble();
            social = RandomUtils.nextDouble();
            dexterity = RandomUtils.nextDouble();
            return this;
        }

        /**
         * Builds the resulting {@link BotPersonality} instance.
         *
         * @return The new personality.
         */
        public BotPersonality build() {
            return new BotPersonality(bot,
                    Math.max(intelligence, 1.0),
                    Math.max(kindness, 1.0),
                    Math.max(confidence, 1.0),
                    Math.max(social, 1.0),
                    Math.max(dexterity, 1.0));
        }
    }

    /**
     * The owning bot.
     */
    private final Bot bot;

    /**
     * The personality manager for this bot.
     */
    private final BotPersonalityManager personalityManager;

    /**
     * Core personality attributes.
     */
    private final double intelligence;
    private final double kindness;
    private final double confidence;
    private final double social;
    private final double dexterity;

    /**
     * Creates a new personality instance.
     *
     * @param bot The owning bot.
     * @param intelligence The bot’s intelligence value.
     * @param kindness The bot’s kindness value.
     * @param confidence The bot’s confidence value.
     * @param social The bot’s sociability value.
     * @param dexterity The bot’s dexterity value.
     */
    private BotPersonality(Bot bot, double intelligence, double kindness, double confidence, double social, double dexterity) {
        this.bot = bot;
        this.intelligence = intelligence;
        this.kindness = kindness;
        this.confidence = confidence;
        this.social = social;
        this.dexterity = dexterity;
        personalityManager = bot.getManager().getPersonalityManager();
    }

    /**
     * Determines if this bot tends to be arrogant.
     *
     * @return {@code true} if arrogance exceeds {@code 0.7}.
     */
    public boolean isArrogant() {
        return getArrogance() >= 0.7;
    }

    /**
     * Determines if this bot is considered intelligent.
     *
     * @return {@code true} if intelligence exceeds {@code 0.7}.
     */
    public boolean isIntelligent() {
        return intelligence >= 0.7;
    }

    /**
     * Determines if this bot is considered dumb.
     *
     * @return {@code true} if intelligence is below {@code 0.3}.
     */
    public boolean isDumb() {
        return intelligence <= 0.3;
    }

    /**
     * Determines if this bot is kind.
     *
     * @return {@code true} if kindness exceeds {@code 0.7}.
     */
    public boolean isKind() {
        return kindness >= 0.7;
    }

    /**
     * Determines if this bot is mean-spirited.
     *
     * @return {@code true} if kindness is below {@code 0.3}.
     */
    public boolean isMean() {
        return kindness <= 0.3;
    }

    /**
     * Determines if this bot is confident.
     *
     * @return {@code true} if confidence exceeds {@code 0.7}.
     */
    public boolean isConfident() {
        return confidence >= 0.7;
    }

    /**
     * Determines if this bot is uncertain or lacks confidence.
     *
     * @return {@code true} if confidence is below {@code 0.3}.
     */
    public boolean isUncertain() {
        return confidence <= 0.3;
    }

    /**
     * Determines if this bot is highly social.
     *
     * @return {@code true} if social exceeds {@code 0.7}.
     */
    public boolean isSocial() {
        return social >= 0.7;
    }

    /**
     * Determines if this bot is antisocial.
     *
     * @return {@code true} if social is below {@code 0.3}.
     */
    public boolean isAntiSocial() {
        return social <= 0.3;
    }

    /**
     * Determines if this bot is dexterous and precise.
     *
     * @return {@code true} if dexterity exceeds {@code 0.7}.
     */
    public boolean isDextrous() {
        return dexterity >= 0.7;
    }

    /**
     * Determines if this bot is clumsy or uncoordinated.
     *
     * @return {@code true} if dexterity is below {@code 0.3}.
     */
    public boolean isClumsy() {
        return dexterity <= 0.3;
    }

    /**
     * Computes a dynamic arrogance score.
     * <p>
     * Arrogance increases with confidence and dexterity, but decreases with intelligence and kindness.
     * The resulting score is typically within the {@code [0.0, 1.0]} range.
     *
     * @return The computed arrogance value.
     */
    public double getArrogance() {
        return (confidence * 0.60) + (dexterity * 0.25)
                - (kindness * 0.60) - (intelligence * 0.25);
    }

    /**
     * @return The raw intelligence value.
     */
    public double getIntelligence() {
        return intelligence;
    }

    /**
     * @return The raw kindness value.
     */
    public double getKindness() {
        return kindness;
    }

    /**
     * @return The raw confidence value.
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * @return The raw social value.
     */
    public double getSocial() {
        return social;
    }

    /**
     * @return The raw dexterity value.
     */
    public double getDexterity() {
        return dexterity;
    }
}