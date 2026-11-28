package io.luna.game.model.mob.bot.brain;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import game.bot.scripts.combat.CombatBotScript.InitialState;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.memory.BotOutlookMemory;
import io.luna.util.RandomUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Manages the emotional state of a {@link Bot}.
 * <p>
 * Each emotion has a baseline score derived from the bot's {@link BotPersonality}. Temporary
 * {@link EmotionalTrigger}s can modify these scores in response to gameplay events.
 * <p>
 * Triggers for one emotion do not simply add up. The strongest counts in full and each one after it half as much as
 * the one before, so one event moves a bot as far as its trigger says and a run of them cannot move it without end.
 * <p>
 * The bot's {@link BotOutlookMemory outlook} moves each score as well. It is the part of an emotion that lasts: how
 * the last day has gone, kept in the bot's memory across logouts.
 * <p>
 * Emotional checks are probabilistic. A higher score makes the bot more likely to feel an emotion, but does not
 * guarantee that the emotion will be felt on every check.
 * <p>
 * Each emotion is rolled once and the result stands for 30 to 90 seconds, so code that checks every tick sees one
 * mood rather than a new roll each time. A new {@link EmotionalTrigger} for an emotion makes its next check roll again.
 * <p>
 * Emotions can be used to influence bot behavior such as combat, trading, speech, skilling, and risk-taking.
 *
 * @author lare96
 */
public final class BotEmotion {

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
     * The shortest time a rolled emotion check stands, in ticks.
     */
    private static final int MINIMUM_HOLD_TICKS = 50;

    /**
     * The longest time a rolled emotion check stands, in ticks.
     */
    private static final int MAXIMUM_HOLD_TICKS = 150;

    /**
     * How much of a trigger counts when a stronger one for the same emotion is active, and again for each one after.
     */
    private static final double FOLLOWING = 0.5;

    /**
     * How far an outlook at its strongest moves an emotion's score.
     */
    private static final double OUTLOOK_SWAY = 0.25;

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
     * The result of the latest roll for each emotion, indexed by {@link EmotionType#ordinal()}.
     */
    private final boolean[] feeling = new boolean[EmotionType.values().length];

    /**
     * The tick each roll in {@link #feeling} stands until. A check at or after that tick rolls again.
     */
    private final long[] feelingUntil = new long[EmotionType.values().length];

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
     * The trigger will modify future checks for its emotion until it expires. Expired triggers across all emotions
     * are removed when a trigger is added; checks also remove expired triggers for the emotion being queried.
     * <p>
     * The next check for the trigger's emotion rolls again, so the bot's mood can change as soon as something happens.
     *
     * @param trigger The trigger to add.
     */
    public void add(EmotionalTrigger trigger) {
        Instant now = Instant.now();
        triggerMap.values().removeIf(existing -> now.isAfter(existing.expire));
        // Something was triggering to our bot :(
        triggerMap.put(trigger.type, trigger);
        feelingUntil[trigger.type.ordinal()] = 0;
    }

    /**
     * Determines whether the bot is currently feeling an emotion.
     * <p>
     * The {@link #score(EmotionType) score} for {@code type} is compared against a random value. If {@code inverse}
     * is enabled, the opposite of that result is returned.
     * <p>
     * The roll stands for {@link #MINIMUM_HOLD_TICKS} to {@link #MAXIMUM_HOLD_TICKS} ticks, or until a trigger is
     * added for {@code type}. Repeated calls in that time return the same result. Rolling on every call would let
     * code that checks each tick pass within a few ticks whatever the score.
     *
     * @param type The emotion to check.
     * @param inverse {@code true} to check that the bot is not feeling the emotion.
     * @return {@code true} if the probability check succeeds.
     */
    public boolean isFeeling(EmotionType type, boolean inverse) {
        int index = type.ordinal();
        long now = bot.getWorld().getCurrentTick();
        if (now >= feelingUntil[index]) {
            feeling[index] = score(type) > RandomUtils.nextDouble();
            feelingUntil[index] = now + RandomUtils.inclusive(MINIMUM_HOLD_TICKS, MAXIMUM_HOLD_TICKS);
        }
        return feeling[index] != inverse;
    }

    /**
     * Works out how likely the bot is to feel an emotion right now, without rolling.
     * <p>
     * The score is the baseline from the bot's personality, moved by its outlook and by its active triggers. It is
     * used as a probability and is not clamped.
     *
     * @param type The emotion.
     * @return The score.
     */
    public double score(EmotionType type) {
        BotPersonality personality = bot.getPersonality();
        double baseline = emotionMap.computeIfAbsent(type, it -> type.computeScore.apply(personality));
        return baseline + OUTLOOK_SWAY * bot.getMemory().getOutlook().of(type) + checkTriggers(type);
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
     * Works out how far the active triggers for an emotion move its score.
     * <p>
     * Expired triggers are removed first.
     *
     * @param type The emotion whose triggers should be applied.
     * @return The amount the triggers add to the score, which is negative if they lower it.
     */
    private double checkTriggers(EmotionType type) {
        Collection<EmotionalTrigger> triggers = triggerMap.get(type);
        if (triggers.isEmpty()) {
            return 0.0;
        }
        // Our bot doesn't care about what happened anymore. Lazy removals here.
        Instant now = Instant.now();
        triggers.removeIf(trigger -> now.isAfter(trigger.expire));

        double[] values = new double[triggers.size()];
        int index = 0;
        for (EmotionalTrigger trigger : triggers) {
            values[index++] = Double.isFinite(trigger.value) ? trigger.value : 0.0;
        }
        return stacked(values);
    }

    /**
     * Adds up trigger values so that each further one counts for less. Values that raise the score and values that
     * lower it are stacked apart: the strongest of each counts in full, the next half as much, the next a quarter,
     * and so on. Neither side can pass twice its strongest value.
     *
     * @param values The trigger values, in any order. The array is sorted.
     * @return The amount they move the score together.
     */
    static double stacked(double[] values) {
        Arrays.sort(values);
        double total = 0.0;
        double share = 1.0;
        for (int index = values.length - 1; index >= 0 && values[index] > 0.0; index--) {
            total += values[index] * share;
            share *= FOLLOWING;
        }
        share = 1.0;
        for (int index = 0; index < values.length && values[index] < 0.0; index++) {
            total += values[index] * share;
            share *= FOLLOWING;
        }
        return total;
    }

    /**
     * Clears all emotional state associated with this bot.
     * <p>
     * All active triggers, cached baseline scores and held rolls are removed. Emotion scores will be recalculated
     * from the bot's current personality the next time they are checked.
     * <p>
     * This should be called when the bot's personality changes.
     */
    public void clear() {
        triggerMap.clear();
        emotionMap.clear();
        Arrays.fill(feelingUntil, 0);
    }
}
