package io.luna.game.model.mob.bot.brain;

import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplate;
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType;
import io.luna.util.RandomUtils;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Defines the personality traits of a {@link Bot}.
 * <p>
 * A personality consists of intelligence, kindness, confidence, sociability, and dexterity values. These traits can
 * be used by bot systems to influence decision-making and behavior.
 * <p>
 * Personalities may be created from a predefined {@link PersonalityTemplate}, randomized from a template with
 * variance, or generated using completely random trait values.
 *
 * @author lare96
 */
public final class BotPersonality {

    /**
     * A builder for creating {@link BotPersonality} instances.
     */
    public static final class Builder {

        /**
         * The manager used to retrieve predefined personality templates.
         */
        private final BotPersonalityManager personalityManager;

        /**
         * The template type used to generate the personality.
         */
        private PersonalityTemplateType type = PersonalityTemplateType.NONE;

        /**
         * The personality trait values. A value of {@code -1} indicates that the trait has not been assigned.
         */
        private double intelligence = -1;
        private double kindness = -1;
        private double confidence = -1;
        private double social = -1;
        private double dexterity = -1;

        /**
         * Creates a new {@link Builder}.
         *
         * @param personalityManager The personality manager.
         */
        public Builder(BotPersonalityManager personalityManager) {
            this.personalityManager = personalityManager;
        }

        /**
         * Sets the intelligence value.
         *
         * @param intelligence The intelligence value.
         * @return This builder.
         */
        public Builder setIntelligence(double intelligence) {
            this.intelligence = intelligence;
            return this;
        }

        /**
         * Sets the kindness value.
         *
         * @param kindness The kindness value.
         * @return This builder.
         */
        public Builder setKindness(double kindness) {
            this.kindness = kindness;
            return this;
        }

        /**
         * Sets the confidence value.
         *
         * @param confidence The confidence value.
         * @return This builder.
         */
        public Builder setConfidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        /**
         * Sets the social value.
         *
         * @param social The social value.
         * @return This builder.
         */
        public Builder setSocial(double social) {
            this.social = social;
            return this;
        }

        /**
         * Sets the dexterity value.
         *
         * @param dexterity The dexterity value.
         * @return This builder.
         */
        public Builder setDexterity(double dexterity) {
            this.dexterity = dexterity;
            return this;
        }

        /**
         * @return The intelligence value.
         */
        public double getIntelligence() {
            return intelligence;
        }

        /**
         * @return The kindness value.
         */
        public double getKindness() {
            return kindness;
        }

        /**
         * @return The confidence value.
         */
        public double getConfidence() {
            return confidence;
        }

        /**
         * @return The social value.
         */
        public double getSocial() {
            return social;
        }

        /**
         * @return The dexterity value.
         */
        public double getDexterity() {
            return dexterity;
        }

        /**
         * Loads all personality values from the specified template.
         *
         * @param from The template type.
         * @return This builder.
         */
        public Builder template(PersonalityTemplateType from) {
            PersonalityTemplate template = personalityManager.getTemplate(from);
            type = template.type;
            intelligence = template.intelligence;
            kindness = template.kindness;
            confidence = template.confidence;
            social = template.social;
            dexterity = template.dexterity;
            return this;
        }

        /**
         * Loads personality values from the specified template and applies a random variance to each trait.
         * <p>
         * Each trait receives an independently generated value between {@code -variance} and {@code variance}.
         *
         * @param from The template type.
         * @param variance The maximum variance applied to each trait.
         * @return This builder.
         */
        public Builder randomizeTemplate(PersonalityTemplateType from, double variance) {
            Supplier<Double> varianceSupplier = () -> ThreadLocalRandom.current().nextDouble(-variance, variance);
            PersonalityTemplate template = personalityManager.getTemplate(from);
            type = template.type;
            intelligence = template.intelligence + varianceSupplier.get();
            kindness = template.kindness + varianceSupplier.get();
            confidence = template.confidence + varianceSupplier.get();
            social = template.social + varianceSupplier.get();
            dexterity = template.dexterity + varianceSupplier.get();
            return this;
        }

        /**
         * Generates a personality from a randomly selected template with randomized variance.
         * <p>
         * The variance is randomly selected between {@code 0.05} inclusive and {@code 0.25} exclusive.
         *
         * @return This builder.
         */
        public Builder randomizeSmart() {
            return randomizeTemplate(RandomUtils.random(PersonalityTemplateType.ALL_EXCEPT_NONE),
                    ThreadLocalRandom.current().nextDouble(0.05, 0.25));
        }

        /**
         * Randomizes every personality trait independently.
         *
         * @return This builder.
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
         * Builds a new {@link BotPersonality} using the configured values.
         * <p>
         * Trait values greater than {@code 1.0} are capped at {@code 1.0}.
         *
         * @return The resulting personality.
         */
        public BotPersonality build() {
            return new BotPersonality(type,
                    Math.min(intelligence, 1.0),
                    Math.min(kindness, 1.0),
                    Math.min(confidence, 1.0),
                    Math.min(social, 1.0),
                    Math.min(dexterity, 1.0));
        }
    }

    /**
     * The default personality, with every trait set to {@code 0.5}.
     */
    public static final BotPersonality DEFAULT = new BotPersonality(PersonalityTemplateType.JACK_OF_ALL_TRADES,
            0.5, 0.5, 0.5, 0.5, 0.5);

    /**
     * The template type this personality was generated from.
     */
    private final PersonalityTemplateType type;

    /**
     * The intelligence value.
     */
    private final double intelligence;

    /**
     * The kindness value.
     */
    private final double kindness;

    /**
     * The confidence value.
     */
    private final double confidence;

    /**
     * The social value.
     */
    private final double social;

    /**
     * The dexterity value.
     */
    private final double dexterity;

    /**
     * Creates a new {@link BotPersonality}.
     *
     * @param type The template type.
     * @param intelligence The intelligence value.
     * @param kindness The kindness value.
     * @param confidence The confidence value.
     * @param social The social value.
     * @param dexterity The dexterity value.
     */
    private BotPersonality(PersonalityTemplateType type, double intelligence, double kindness, double confidence,
                           double social, double dexterity) {
        this.type = type;
        this.intelligence = intelligence;
        this.kindness = kindness;
        this.confidence = confidence;
        this.social = social;
        this.dexterity = dexterity;
    }

    /**
     * Determines whether this personality is considered intelligent.
     *
     * @return {@code true} if intelligence is at least {@code 0.7}.
     */
    public boolean isIntelligent() {
        return intelligence >= 0.7;
    }

    /**
     * Determines whether this personality is considered dumb.
     *
     * @return {@code true} if intelligence is at most {@code 0.3}.
     */
    public boolean isDumb() {
        return intelligence <= 0.3;
    }

    /**
     * Determines whether this personality is considered kind.
     *
     * @return {@code true} if kindness is at least {@code 0.7}.
     */
    public boolean isKind() {
        return kindness >= 0.7;
    }

    /**
     * Determines whether this personality is considered mean.
     *
     * @return {@code true} if kindness is at most {@code 0.3}.
     */
    public boolean isMean() {
        return kindness <= 0.3;
    }

    /**
     * Determines whether this personality is considered confident.
     *
     * @return {@code true} if confidence is at least {@code 0.7}.
     */
    public boolean isConfident() {
        return confidence >= 0.7;
    }

    /**
     * Determines whether this personality combines low intelligence with high confidence.
     *
     * @return {@code true} if this personality is both dumb and confident.
     */
    public boolean isStupidlyConfident() {
        return isDumb() && isConfident();
    }

    /**
     * Determines whether this personality is considered uncertain.
     *
     * @return {@code true} if confidence is at most {@code 0.3}.
     */
    public boolean isUncertain() {
        return confidence <= 0.3;
    }

    /**
     * Determines whether this personality is considered social.
     *
     * @return {@code true} if the social value is at least {@code 0.7}.
     */
    public boolean isSocial() {
        return social >= 0.7;
    }

    /**
     * Determines whether this personality is considered antisocial.
     *
     * @return {@code true} if the social value is at most {@code 0.3}.
     */
    public boolean isAntiSocial() {
        return social <= 0.3;
    }

    /**
     * Determines whether this personality is considered dexterous.
     *
     * @return {@code true} if dexterity is at least {@code 0.7}.
     */
    public boolean isDextrous() {
        return dexterity >= 0.7;
    }

    /**
     * Determines whether this personality is considered clumsy.
     *
     * @return {@code true} if dexterity is at most {@code 0.3}.
     */
    public boolean isClumsy() {
        return dexterity <= 0.3;
    }

    /**
     * Returns the template type this personality was generated from.
     * <p>
     * {@link PersonalityTemplateType#NONE} indicates that the personality was not generated from a predefined
     * template. This method never returns {@code null}.
     *
     * @return The personality template type.
     */
    public PersonalityTemplateType getType() {
        if (type == null) {
            return PersonalityTemplateType.NONE;
        }
        return type;
    }

    /**
     * @return The intelligence value.
     */
    public double getIntelligence() {
        return intelligence;
    }

    /**
     * @return The kindness value.
     */
    public double getKindness() {
        return kindness;
    }

    /**
     * @return The confidence value.
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * @return The social value.
     */
    public double getSocial() {
        return social;
    }

    /**
     * @return The dexterity value.
     */
    public double getDexterity() {
        return dexterity;
    }
}