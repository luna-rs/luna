package io.luna.game.model.mob.bot.brain;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import game.bot.scripts.combat.CombatBotScript.InitialState;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.bot.Bot;
import io.luna.util.RandomUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

/**
 * Tracks the emotional state of a {@link Bot}.
 * <p>
 * A bot's emotions are derived from its {@link BotPersonality} and adjusted by temporary {@link EmotionalTrigger}s.
 * These values are intentionally probabilistic, so a bot with a high score for an emotion is more likely to feel
 * that emotion, but is not guaranteed to feel it every time.
 * <p>
 * This gives bots a lightweight mood system that can influence speech, risk-taking, trading, combat, skilling,
 * social behavior, and other high-level decisions without requiring a complex simulation.
 * <p>
 * Example usage:
 * <pre>
 * {@code if (bot.getEmotions().isFeeling(BotEmotion.EmotionType.HAPPY)) {
 *     bot.getSpeechStack().push(new BotSpeech("What a beautiful day!"));
 * }}
 * </pre>
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
    //  Bots will eat food at a health correlating to their confidence.

    /**
     * The emotions a bot can experience.
     * <p>
     * Each emotion defines a baseline scoring formula based on the bot's personality. The baseline is later adjusted
     * by active {@link EmotionalTrigger}s when {@link BotEmotion#isFeeling(EmotionType)} is called.
     * <p>
     * Scores are intended to generally stay within the {@code 0.0} to {@code 1.0} probability range, but temporary
     * triggers may push them above or below that range. A score above {@code 1.0} will almost always pass the random
     * check, while a score below {@code 0.0} will almost never pass.
     */
    public enum EmotionType {

        /**
         * A positive, relaxed, or socially open mood.
         * <p>
         * Higher values represent a happier, calmer, more socially open bot. Lower values represent an angrier, more
         * irritated, or more hostile bot.
         */
        HAPPY(personality -> (personality.getSocial() * 0.30)
                + (personality.getKindness() * 0.30)
                + (personality.getConfidence() * 0.20)
                + (personality.getIntelligence() * 0.10)
                - (personality.getArrogance() * 0.40)),

        /**
         * A cautious, anxious, or danger-avoidant mood.
         * <p>
         * Fear is mostly the inverse of confidence. Scared bots may be more likely to avoid dangerous zones, hesitate
         * before entering the Wilderness, avoid bossing, flee combat, or choose safer money-making methods.
         */
        SCARED(personality -> 1.0 - (personality.getConfidence() * 0.85)),

        /**
         * A selfish, opportunistic, or profit-seeking mood.
         * <p>
         * Greed rises with confidence and intelligence, but is reduced by dexterity and kindness. Greedy bots may be
         * more likely to chase profitable items, loot aggressively, merchant, scam, or prioritize wealth over safety.
         */
        GREEDY(personality -> (personality.getConfidence() * 0.60) + (personality.getIntelligence() * 0.25)
                - (personality.getDexterity() * 0.25) - (personality.getKindness() * 0.60));

        /**
         * Computes this emotion's baseline score from a bot personality.
         */
        private final Function<BotPersonality, Double> computeScore;

        EmotionType(Function<BotPersonality, Double> result) {
            this.computeScore = result;
        }
    }

    /**
     * A temporary modifier that raises or lowers a specific emotion.
     * <p>
     * Triggers represent short-lived emotional reactions caused by gameplay events. Examples include winning a fight,
     * losing a fight, receiving a valuable drop, failing to find an item, or completing a good trade.
     * <p>
     * Expired triggers are removed lazily the next time their emotion type is checked.
     */
    public static final class EmotionalTrigger {

        /**
         * The emotion affected by this trigger.
         */
        private final EmotionType type;

        /**
         * The amount added to the emotion's current score.
         * <p>
         * Positive values increase the chance that the bot feels this emotion. Negative values reduce the chance.
         */
        private final double value;

        /**
         * The time this trigger stops affecting the bot.
         */
        private final Instant expire;

        /**
         * Creates a new {@link EmotionalTrigger}.
         *
         * @param type The emotion affected by this trigger.
         * @param value The amount added to the emotion score until this trigger expires.
         * @param expire The expiration time for this trigger.
         */
        public EmotionalTrigger(EmotionType type, double value, Instant expire) {
            this.type = type;
            this.value = value;
            this.expire = expire;
        }

        /**
         * Creates a new {@link EmotionalTrigger} that expires after 30 minutes.
         *
         * @param type The emotion affected by this trigger.
         * @param value The amount added to the emotion score until this trigger expires.
         */
        public EmotionalTrigger(EmotionType type, double value) {
            this(type, value, Instant.now().plus(30, ChronoUnit.MINUTES));
        }
    }

    /**
     * Active temporary emotion modifiers grouped by emotion type.
     * <p>
     * Multiple triggers can affect the same emotion at once. For example, a bot may become happier from a successful
     * trade while also becoming greedier from seeing a valuable item.
     */
    private final Multimap<EmotionType, EmotionalTrigger> triggerMap = ArrayListMultimap.create();

    /**
     * Cached baseline emotion scores.
     * <p>
     * These values are computed from the bot's personality the first time each emotion is checked. Temporary triggers
     * are not stored in this map; they are applied on top of the cached baseline during each call to {@link #isFeeling(EmotionType)}.
     */
    private final Map<EmotionType, Double> emotionMap = new EnumMap<>(EmotionType.class);

    /**
     * The bot this emotional state belongs to.
     */
    private final Bot bot;

    /**
     * Creates a new emotional state tracker for a bot.
     *
     * @param bot The bot this emotional state belongs to.
     */
    public BotEmotion(Bot bot) {
        this.bot = bot;
    }

    /**
     * Adds a temporary emotional trigger.
     * <p>
     * The trigger will affect future calls to {@link #isFeeling(EmotionType, boolean)} for its emotion type until it
     * expires and is lazily removed.
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
     * This method starts with the bot's cached baseline score for {@code type}, applies all active temporary triggers
     * for that emotion, optionally inverts the final score, then compares the result against a random roll.
     * <p>
     * The {@code inverse} flag is useful for emotions modeled as a single axis. For example, a high {@code HAPPY} score
     * can represent happiness, while the inverse of that same score can represent anger or irritation.
     * <p>
     * This means emotional checks are probability-based. Calling this method repeatedly may produce different answers
     * even when the underlying score has not changed.
     *
     * @param type The emotion type to check.
     * @param inverse {@code true} to check the opposite end of this emotion axis.
     * @return {@code true} if the bot is currently feeling the requested emotion state.
     */
    public boolean isFeeling(EmotionType type, boolean inverse) {
        BotPersonality personality = bot.getPersonality();
        double score = emotionMap.computeIfAbsent(type, it -> type.computeScore.apply(personality));
        score = checkTriggers(type, score);
        score = inverse ? (1.0 - score) : score;
        return score > RandomUtils.nextDouble();
    }

    /**
     * Determines whether the bot is currently feeling the normal side of an emotion axis.
     * <p>
     * This is equivalent to calling {@link #isFeeling(EmotionType, boolean)} with {@code inverse} set to {@code false}.
     *
     * @param type The emotion type to check.
     * @return {@code true} if the bot is currently feeling the requested emotion.
     */
    public boolean isFeeling(EmotionType type) {
        return isFeeling(type, false);
    }

    /**
     * Checks whether this bot should treat its current hitpoints as dangerously low.
     * <p>
     * This is a personality-driven fear check. Bots with lower confidence become nervous at higher health percentages,
     * while more confident bots are willing to stay in danger longer before reacting. If the bot is currently feeling
     * {@link EmotionType#SCARED}, its effective confidence is reduced, making it more cautious than usual.
     * <p>
     * Some low-health states are treated as urgent regardless of percentage:
     * <ul>
     *     <li>Bots with more than 20 base Hitpoints become nervous below 10 current Hitpoints.</li>
     *     <li>All bots become nervous below 5 current Hitpoints.</li>
     * </ul>
     * These urgent checks are ignored only for bots that are {@link BotPersonality#isStupidlyConfident()}.
     * <p>
     * For normal checks, the nervous threshold is calculated from confidence and is clamped to a minimum of {@code 5%},
     * ensuring even highly confident bots still react when critically low.
     *
     * @return {@code true} if the bot should be nervous about its current hitpoints.
     */
    public boolean isNervousAboutHp() {
        if(bot.getHealthPercent() <= 10) {
            return true;
        }
        Skill hitpoints = bot.getSkills().getSkill(Skill.HITPOINTS);
        boolean alwaysNervous = (hitpoints.getStaticLevel() > 20 && hitpoints.getLevel() < 10) || hitpoints.getLevel() < 5 || bot.getHealthPercent() <= 10;
        if (alwaysNervous && !bot.getPersonality().isStupidlyConfident()) {
            return true;
        }
        double confidence = isFeeling(EmotionType.SCARED) ? bot.getPersonality().getConfidence() * 0.75
                : bot.getPersonality().getConfidence();
        return bot.getHealthPercent() < Math.max(50 * (1.0 - confidence), 5.0);
    }

    /**
     * Chooses this bot's initial combat response when attacked.
     * <p>
     * The decision is intentionally personality-driven and probabilistic:
     * <ul>
     *     <li>Against non-threatening attackers, confidence matters most because the bot is deciding whether it feels
     *     bold enough to fight back.</li>
     *     <li>Against threatening attackers, intelligence matters most because smarter bots are more likely to recognize
     *     danger and retreat.</li>
     * </ul>
     * <p>
     * This method only chooses the first reaction. Longer-term combat behavior, such as eating, fleeing, pathing,
     * or re-engaging, should be handled by the combat action logic after this state is selected.
     *
     * @param attacker The mob that attacked this bot.
     *
     * @return {@link InitialState#ATTACK} if the bot decides to fight back, or {@link InitialState#RUN} if it decides
     * to retreat.
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
     * Applies all active triggers for an emotion.
     * <p>
     * Expired triggers are removed during iteration. Active triggers add their modifier value to the
     * supplied baseline score.
     *
     * @param type The emotion type whose triggers should be applied.
     * @param value The baseline emotion score before temporary modifiers.
     * @return The emotion score after active trigger modifiers are applied.
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
     * Clears all cached and temporary emotional state for this bot.
     * <p>
     * This removes all active {@link EmotionalTrigger}s and clears the cached baseline emotion scores. The next call to
     * {@link #isFeeling(EmotionType)} will recompute the requested emotion from the bot's current {@link BotPersonality}.
     * <p>
     * This is useful for when the bot's personality changes through {@link Bot#setPersonality(BotPersonality)}.
     */
    public void clear() {
        triggerMap.clear();
        emotionMap.clear();
    }
}