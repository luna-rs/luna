package io.luna.game.model.mob.bot.brain;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import game.bot.scripts.combat.CombatBotScript.InitialState;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.bot.Bot;
import io.luna.util.RandomUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

/**
 * Manages the emotional state of a {@link Bot}.
 * <p>
 * Each emotion has a baseline score derived from the bot's {@link BotPersonality}. Temporary
 * {@link EmotionalTrigger}s can modify these scores in response to gameplay events.
 * <p>
 * Emotional checks are probabilistic. A higher score makes the bot more likely to feel an emotion, but does not
 * guarantee that the emotion will be felt on every check.
 * <p>
 * Emotions can be used to influence bot behavior such as combat, trading, speech, skilling, and risk-taking.
 *
 * @author lare96
 */
public final class BotEmotion {

    // TODO@0.5.0 Implement this in more scenarios.
    //  Completed a successful trade where they got something they needed (greed down, happy up, anger down).
    //  Wasn't able to find the item they're searching for (greed up, happy down, anger up).
    //  Successfully PKed a player (greed down, happy up).
    //  Check if scared before trying to PK someone, bossing, entering the wild to do certain activities.
    //  Check if feeling greedy, angry, and if intelligent, social, confident before scamming.
    //  Emotion also influences the type of chat bots will speak (change general chat into the 4 emotional categories).

    /**
     * The emotions a bot can experience.
     * <p>
     * Each emotion defines a baseline score calculated from the bot's personality. Temporary
     * {@link EmotionalTrigger}s are applied to this baseline when the emotion is checked.
     * <p>
     * Scores are used directly as probabilities and are not clamped. Values at or above {@code 1.0} will always pass
     * the probability check, while values at or below {@code 0.0} will never pass it.
     */
    public enum EmotionType {

        /**
         * Represents happiness, calmness, and general positive disposition.
         * <p>
         * Happiness increases with sociability, kindness, confidence, and intelligence, while arrogance reduces it.
         */
        HAPPY(personality -> (personality.getSocial() * 0.30)
                + (personality.getKindness() * 0.30)
                + (personality.getConfidence() * 0.20)
                + (personality.getIntelligence() * 0.10)
                - ((((personality.getConfidence() * 0.60)
                + (personality.getDexterity() * 0.25)
                + ((1.0 - personality.getKindness()) * 0.60)
                + ((1.0 - personality.getIntelligence()) * 0.25)) / 1.70) * 0.40)),

        /**
         * Represents fear, caution, and danger avoidance.
         * <p>
         * Fear is primarily determined by the inverse of confidence. Less confident bots are therefore more likely to
         * feel scared.
         */
        SCARED(personality -> 1.0 - (personality.getConfidence() * 0.85)),

        /**
         * Represents greed, opportunism, and a preference for personal gain.
         * <p>
         * Greed increases with confidence and intelligence, while kindness and dexterity reduce it.
         */
        GREEDY(personality -> (personality.getConfidence() * 0.60) + (personality.getIntelligence() * 0.25)
                - (personality.getDexterity() * 0.25) - (personality.getKindness() * 0.60));

        /**
         * Computes the baseline score for this emotion from a bot's personality.
         */
        private final Function<BotPersonality, Double> computeScore;

        /**
         * Creates a new emotion type.
         *
         * @param result The function used to calculate its baseline score.
         */
        EmotionType(Function<BotPersonality, Double> result) {
            this.computeScore = result;
        }
    }

    /**
     * A temporary modifier applied to an {@link EmotionType}.
     * <p>
     * Emotional triggers represent reactions to gameplay events and remain active until their expiration time.
     */
    public static final class EmotionalTrigger {

        /**
         * The emotion modified by this trigger.
         */
        private final EmotionType type;

        /**
         * The amount added to the emotion score.
         */
        private final double value;

        /**
         * The time at which this trigger expires.
         */
        private final Instant expire;

        /**
         * Creates a new emotional trigger.
         *
         * @param type The emotion to modify.
         * @param value The amount added to its score.
         * @param expire The time at which the trigger expires.
         */
        public EmotionalTrigger(EmotionType type, double value, Instant expire) {
            this.type = type;
            this.value = value;
            this.expire = expire;
        }

        /**
         * Creates a new emotional trigger that expires after 30 minutes.
         *
         * @param type The emotion to modify.
         * @param value The amount added to its score.
         */
        public EmotionalTrigger(EmotionType type, double value) {
            this(type, value, Instant.now().plus(30, ChronoUnit.MINUTES));
        }
    }

    /**
     * The active temporary emotion modifiers grouped by emotion type.
     * <p>
     * Multiple triggers may affect the same emotion simultaneously.
     */
    private final Multimap<EmotionType, EmotionalTrigger> triggerMap = ArrayListMultimap.create();

    /**
     * The cached baseline score for each emotion.
     * <p>
     * Baselines are calculated from the bot's personality when first requested. Temporary triggers are applied
     * separately and are not stored in this map.
     */
    private final Map<EmotionType, Double> emotionMap = new EnumMap<>(EmotionType.class);

    /**
     * The bot this emotional state belongs to.
     */
    private final Bot bot;

    /**
     * Creates a new emotional state for the specified bot.
     *
     * @param bot The bot.
     */
    public BotEmotion(Bot bot) {
        this.bot = bot;
    }

    /**
     * Adds a temporary emotional trigger.
     * <p>
     * The trigger will modify future checks for its emotion until it expires. Expired triggers are removed lazily
     * when that emotion is checked.
     *
     * @param trigger The trigger to add.
     */
    public void add(EmotionalTrigger trigger) {
        // Something was triggering to our bot :(
        triggerMap.put(trigger.type, trigger);
    }

    /**
     * Determines whether the bot is currently feeling an emotion.
     * <p>
     * The cached baseline score for {@code type} is combined with all active triggers before being compared against
     * a random value. If {@code inverse} is enabled, {@code 1.0 - score} is checked instead.
     * <p>
     * Because the final check is probabilistic, repeated calls may return different results without the underlying
     * emotional state changing.
     *
     * @param type The emotion to check.
     * @param inverse {@code true} to check the inverse of the final emotion score.
     * @return {@code true} if the probability check succeeds.
     */
    public boolean isFeeling(EmotionType type, boolean inverse) {
        BotPersonality personality = bot.getPersonality();
        double score = emotionMap.computeIfAbsent(type, it -> type.computeScore.apply(personality));
        score = checkTriggers(type, score);
        score = inverse ? (1.0 - score) : score;
        return score > RandomUtils.nextDouble();
    }

    /**
     * Determines whether the bot is currently feeling an emotion.
     *
     * @param type The emotion to check.
     * @return {@code true} if the probability check succeeds.
     */
    public boolean isFeeling(EmotionType type) {
        return isFeeling(type, false);
    }

    /**
     * Determines whether the bot considers its current health dangerously low.
     * <p>
     * A bot is always nervous at or below {@code 20%} health. Bots that are not
     * {@link BotPersonality#isStupidlyConfident()} are also always nervous at or below {@code 35%} health.
     * <p>
     * Above these thresholds, the nervous threshold is determined by confidence. A bot with no confidence may
     * become nervous as high as {@code 75%} health, while increasing confidence progressively lowers this threshold.
     * <p>
     * If the bot is currently {@link EmotionType#SCARED}, its effective confidence is reduced by {@code 25%}, causing
     * it to become nervous at a higher health percentage.
     *
     * @return {@code true} if the bot considers its current health dangerous.
     */
    public boolean isNervousAboutHp() {
        int highestNervousHp = 75; // Highest HP bot can start to feel nervous.
        int smartNervousHp = 35; // HP that will trigger the "always nervous" threshold for smarter/timid bots.
        int minimumNervousHp = 20; // HP that will trigger the "always nervous" threshold for all bots.
        if (bot.getHealthPercent() <= minimumNervousHp) {
            return true;
        }
        if (bot.getHealthPercent() <= smartNervousHp && !bot.getPersonality().isStupidlyConfident()) {
            return true;
        }
        double confidence = isFeeling(EmotionType.SCARED) ? bot.getPersonality().getConfidence() * 0.75
                : bot.getPersonality().getConfidence();

        return bot.getHealthPercent() <= Math.max(highestNervousHp * (1.0 - confidence), minimumNervousHp);
    }

    /**
     * Chooses the bot's initial response after being attacked.
     * <p>
     * If the attacker is considered a threat, intelligence primarily determines whether the bot retreats while
     * confidence reduces that tendency slightly.
     * <p>
     * If the attacker is not considered a threat, confidence primarily determines whether the bot fights back while
     * intelligence provides a smaller influence.
     * <p>
     * This method only determines the bot's initial response. Continued combat behavior is handled elsewhere.
     *
     * @param attacker The mob attacking this bot.
     * @return {@link InitialState#ATTACK} if the bot decides to fight, otherwise {@link InitialState#RUN}.
     */
    public InitialState getCombatResponse(Mob attacker) {
        double confidence = bot.getPersonality().getConfidence();
        double intelligence = bot.getPersonality().getIntelligence();

        /*
         * When the attacker is not an obvious threat, confidence has the strongest influence.
         * Intelligent bots are still more likely to make a stable decision instead of panicking.
         */
        double nonThreatFactor = (confidence * 0.70) + (intelligence * 0.30);

        /*
         * When the attacker is dangerous, intelligence becomes the main factor.
         * Confidence slightly reduces the chance to run, allowing brave bots to sometimes stand their ground.
         */
        double threatFactor = Math.max(0.0, intelligence - (confidence * 0.30));

        if (bot.getActionHandler().getCombat().isThreat(attacker)) {
            return RandomUtils.roll(threatFactor) ? InitialState.RUN : InitialState.ATTACK;
        } else {
            return RandomUtils.roll(nonThreatFactor) ? InitialState.ATTACK : InitialState.RUN;
        }
    }

    /**
     * Applies all active triggers for an emotion to its baseline score.
     * <p>
     * Expired triggers are removed while iterating. Each active trigger adds its value directly to the supplied
     * score.
     *
     * @param type The emotion whose triggers should be applied.
     * @param value The baseline emotion score.
     * @return The score after applying all active triggers.
     */
    private double checkTriggers(EmotionType type, double value) {
        Iterator<EmotionalTrigger> it = triggerMap.get(type).iterator();
        while (it.hasNext()) {
            EmotionalTrigger trigger = it.next();
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

    /**
     * Clears all emotional state associated with this bot.
     * <p>
     * All active triggers and cached baseline scores are removed. Emotion scores will be recalculated from the bot's
     * current personality the next time they are checked.
     * <p>
     * This should be called when the bot's personality changes.
     */
    public void clear() {
        triggerMap.clear();
        emotionMap.clear();
    }
}