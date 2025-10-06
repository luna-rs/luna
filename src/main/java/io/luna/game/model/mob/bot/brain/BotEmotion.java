package io.luna.game.model.mob.bot.brain;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.luna.game.model.mob.bot.Bot;
import io.luna.util.RandomUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

/**
 * Represents the emotional state of a {@link Bot}.
 * <p>
 * A bot's emotions are dynamically computed from its {@link BotPersonality} and modulated by temporary
 * {@link EmotionTrigger}s. These values fluctuate over time and influence behavioral decisions,
 * adding human-like inconsistency to bots.
 *
 * <p>Example usage:
 * <pre>
 *     if (bot.emotions.isFeeling(EmotionType.HAPPY)) {
 *         bot.speechStack.push(new BotSpeech("What a beautiful day!"));
 *     }
 * </pre>
 *
 * @author lare96
 */
public final class BotEmotion {

    /**
     * Represents all emotion types a bot can experience.
     * <p>
     * Each type defines a baseline formula that calculates a default emotional score derived from the bot's
     * personality. The formulas are designed to leave ~15% headroom so they can be modulated with triggers
     * without too much overflow.
     */
    public enum EmotionType {

        /**
         * Happiness is tied to how social and confident a bot is.
         */
        HAPPY(personality -> (personality.getSocial() * 0.55) + (personality.getConfidence() * 0.30)),

        /**
         * Anger rises with confidence and arrogance, but is reduced by kindness, intelligence, and sociability.
         */
        ANGRY(personality -> (personality.getConfidence() * 0.25) + (personality.getArrogance() * 0.60)
                - (personality.getKindness() * 0.60) - (personality.getSocial() * 0.15) - (personality.getIntelligence() * 0.10)),

        /**
         * Fear is inversely related to confidence, high-confidence bots are rarely scared.
         */
        SCARED(personality -> 1.0 - (personality.getConfidence() * 0.85)),

        /**
         * Greed emerges from confidence and intelligence, but is tempered by dexterity
         * (practicality) and kindness (empathy).
         */
        GREEDY(personality -> (personality.getConfidence() * 0.60) + (personality.getIntelligence() * 0.25)
                - (personality.getDexterity() * 0.25) - (personality.getKindness() * 0.60));

        /**
         * The formula used to compute the base emotional score from the bot’s personality.
         */
        private final Function<BotPersonality, Double> computeScore;

        EmotionType(Function<BotPersonality, Double> result) {
            this.computeScore = result;
        }
    }

    /**
     * Represents a temporary emotional stimulus that alters an emotion’s baseline score.
     * <p>
     * Triggers decay automatically after their {@link #expire} time, simulating short-lived
     * emotional reactions like being startled, insulted, or rewarded.
     */
    public static final class EmotionTrigger {

        /**
         * The emotion affected by this trigger.
         */
        private final EmotionType type;

        /**
         * The amount this trigger modifies the emotion score by.
         * Positive values strengthen the emotion; negative values suppress it.
         */
        private final double value;

        /**
         * The expiration timestamp after which this trigger no longer applies.
         */
        private final Instant expire;

        /**
         * Creates a new trigger that modifies the specified emotion until it expires.
         *
         * @param type The emotion to modify.
         * @param value The value to add to the emotion’s baseline score.
         * @param expire The time when this trigger expires.
         */
        public EmotionTrigger(EmotionType type, double value, Instant expire) {
            this.type = type;
            this.value = value;
            this.expire = expire;
        }

        /**
         * Creates a new trigger with a default 30-minute duration.
         *
         * @param type The emotion to modify.
         * @param value The value to add to the emotion’s baseline score.
         */
        public EmotionTrigger(EmotionType type, double value) {
            this(type, value, Instant.now().plus(30, ChronoUnit.MINUTES));
        }
    }

    /**
     * A map of all active emotional triggers currently influencing this bot.
     */
    private final Multimap<EmotionType, EmotionTrigger> triggerMap = ArrayListMultimap.create();

    /**
     * The cached emotional baseline values for each emotion type.
     */
    private final Map<EmotionType, Double> emotionMap = new EnumMap<>(EmotionType.class);

    /**
     * The bot's personality.
     */
    private final BotPersonality personality;

    /**
     * Creates a new {@link BotEmotion}.
     *
     * @param personality The bot's personality.
     */
    public BotEmotion(BotPersonality personality) {
        this.personality = personality;
    }

    /**
     * Adds a temporary emotional trigger that modifies one or more emotion types.
     *
     * @param trigger The trigger to add.
     */
    public void addTrigger(EmotionTrigger trigger) {
        // Something was triggering to our bot :(
        triggerMap.put(trigger.type, trigger);
    }

    /**
     * Determines whether the bot is currently “feeling” a given emotion.
     * <p>
     * The method combines the bot’s baseline score for that emotion with any active triggers,
     * then performs a probabilistic check to simulate mood variability.
     *
     * @param type The emotion type to test.
     * @return {@code true} if the bot is currently feeling that emotion.
     */
    public boolean isFeeling(EmotionType type) {
        // The emotional score.
        double score = emotionMap.computeIfAbsent(type, it -> type.computeScore.apply(personality));
        score = checkTriggers(type, score); // Modulate score depending on emotional triggers.
        return score > RandomUtils.nextDouble();
    }

    /**
     * Applies all active triggers for a given emotion, removing expired ones.
     *
     * @param type The emotion type to check.
     * @param value The current emotion value before modulation.
     * @return The adjusted emotion score.
     */
    private double checkTriggers(EmotionType type, double value) {
        Iterator<EmotionTrigger> it = triggerMap.get(type).iterator();
        while (it.hasNext()) {
            EmotionTrigger trigger = it.next();
            Instant now = Instant.now();
            if (now.isAfter(trigger.expire)) {
                // Our bot doesn't care about what happened anymore. Lazy removals here.
                it.remove();
                continue;
            }
            // Modulate their emotional score.
            value += trigger.value;
        }
        return value;
    }
}
