package io.luna.game.model.mob.bot.brain;

import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.BotManager;
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator;

/**
 * Represents the complete cognitive system of a {@link Bot}, encompassing all instinctive, deliberative, emotional,
 * and personality-driven components. Each subsystem is processed in sequence on every game tick, allowing bots to
 * act, react, and plan dynamically in the world.
 *
 * <h3>Processing Pipeline</h3>
 * <ol>
 *     <li>Evaluate immediate instincts through {@link BotReflex}.</li>
 *     <li>If no instinct triggers, evaluate higher reasoning via {@link BotBrain}.</li>
 *     <li>Use {@link BotPersonality}, {@link BotEmotion}, and {@link BotPreference}
 *     to influence decision-making outcomes.</li>
 * </ol>
 *
 * @author lare96
 * @see io.luna.game.model.mob.bot.brain
 */
public final class BotIntelligence {

    /**
     * A builder class used to create and initialize new {@link BotIntelligence} instances. If any subsystem is
     * omitted, a sensible default will be generated automatically.
     */
    public static final class Builder {

        private final BotPersonalityManager personalityManager;
        private BotReflex reflex;
        private BotBrain brain;
        private BotPersonality personality;
        private BotPreference preferences;

        /**
         * Creates a new {@link Builder} instance.
         *
         * @param manager The {@link BotManager} used to access the {@link BotPersonalityManager}.
         */
        public Builder(BotManager manager) {
            personalityManager = manager.getPersonalityManager();
        }

        /**
         * Sets a custom reflex system.
         *
         * @param reflex The reflex system to assign.
         * @return This builder instance.
         */
        public Builder setReflex(BotReflex reflex) {
            this.reflex = reflex;
            return this;
        }

        /**
         * Sets a custom brain system.
         *
         * @param brain The brain system to assign.
         * @return This builder instance.
         */
        public Builder setBrain(BotBrain brain) {
            this.brain = brain;
            return this;
        }

        /**
         * Sets a custom personality profile.
         *
         * @param personality The personality to assign.
         * @return This builder instance.
         */
        public Builder setPersonality(BotPersonality personality) {
            this.personality = personality;
            return this;
        }

        /**
         * Sets a custom preference model.
         *
         * @param preferences The preference model to assign.
         * @return This builder instance.
         */
        public Builder setPreferences(BotPreference preferences) {
            this.preferences = preferences;
            return this;
        }

        /**
         * Builds a complete {@link BotIntelligence} instance.
         * <p>
         * If any subsystem is missing, a randomized or default variant will be created:
         * <ul>
         *     <li>{@link BotReflex} defaults to a new instance.</li>
         *     <li>{@link BotBrain} defaults to a new instance.</li>
         *     <li>{@link BotPersonality} is randomized via the personality manager.</li>
         *     <li>{@link BotPreference} is randomized and derived from the personality.</li>
         * </ul>
         *
         * @return A fully initialized {@link BotIntelligence} object.
         */
        public BotIntelligence build() {
            if (reflex == null) {
                reflex = new BotReflex();
            }
            if (brain == null) {
                brain = new BotBrain();
            }
            if (personality == null) {
                personality = new BotPersonality.Builder(personalityManager).randomizeSmart().build();
            }
            if (preferences == null) {
                preferences = new BotPreference.Builder(personalityManager, personality).randomizeSmart().build();
            }
            return new BotIntelligence(reflex, brain, personality, preferences);
        }
    }

    /**
     * The reflex system, handling immediate instinctive reactions.
     */
    private final BotReflex reflex;

    /**
     * The brain system, handling higher-level reasoning and planning.
     */
    private final BotBrain brain;

    /**
     * The personality system, handling character traits.
     */
    private final BotPersonality personality;

    /**
     * The emotional system, handling moods.
     */
    private final BotEmotion emotions;

    /**
     * The preference system, handling likes and dislikes.
     */
    private final BotPreference preferences;

    /**
     * If the intelligence processing has been started.
     */
    private boolean started = false;

    /**
     * Creates a new {@link BotIntelligence}.
     *
     * @param reflex The reflex.
     * @param brain The brain.
     */
    public BotIntelligence(BotReflex reflex, BotBrain brain, BotPersonality personality,
                           BotPreference preference) {
        this.reflex = reflex;
        this.brain = brain;
        this.personality = personality;
        this.preferences = preference;
        emotions = new BotEmotion(personality);
    }

    /**
     * Marks this intelligence system as started, enabling AI processing.
     */
    public void start() {
        started = true;
    }

    /**
     * Processes a bot’s AI for the current game tick.
     * <p>
     * The execution pipeline is:
     * <ul>
     *     <li>Run {@link BotReflex#process(Bot)} to check for instinctive reactions.</li>
     *     <li>If no reflex triggered, and our bot isn't doing anything, run {@link BotBrain#process(Bot)}.</li>
     *     <li>Execute the returned {@link BotCoordinator}, if one was chosen.</li>
     * </ul>
     *
     * @param bot The bot to process intelligence for.
     */
    public void process(Bot bot) {
        if (!started) {
            return;
        }
        // First process any instincts our bot has.
        if (reflex.process(bot)) {
            // Short-circuit if we still have stuff to do (scripts still in buffer).
            if (!bot.getScriptStack().process()) {
                return;
            }

            // Pick a coordinator to run based on current state.
            BotCoordinator coordinator = brain.process(bot);
            if (coordinator != null) {
                coordinator.accept(bot);
            }
        }
    }

    /**
     * @return The reflex system, handling immediate instinctive reactions.
     */
    public BotReflex getReflex() {
        return reflex;
    }

    /**
     * @return The brain system, handling higher-level reasoning and planning.
     */
    public BotBrain getBrain() {
        return brain;
    }

    /**
     * @return The personality system, handling character traits.
     */
    public BotPersonality getPersonality() {
        return personality;
    }

    /**
     * @return The emotional system, handling moods.
     */
    public BotEmotion getEmotions() {
        return emotions;
    }

    /**
     * @return The preference system, handling likes and dislikes.
     */
    public BotPreference getPreferences() {
        return preferences;
    }
}
