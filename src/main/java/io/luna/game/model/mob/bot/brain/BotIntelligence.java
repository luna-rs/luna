package io.luna.game.model.mob.bot.brain;

import api.bot.BotScript;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator;

/**
 * Encapsulates the entire cognitive model of a {@link Bot}, combining both its instinctual and deliberative
 * behavior layers.
 *
 * @author lare96
 * @see BotReflex
 * @see BotBrain
 * @see BotCoordinator
 * @see BotScript
 */
public final class BotIntelligence {

    /**
     * The bot.
     */
    private final Bot bot;

    /**
     * The reflex system, handling immediate instinctive reactions.
     */
    private final BotReflex reflex;

    /**
     * The brain system, handling higher-level reasoning and planning.
     */
    private final BotBrain brain;

    /**
     * Creates a new {@link BotIntelligence}.
     *
     * @param bot The bot.
     * @param reflex The reflex.
     * @param brain The brain.
     */
    public BotIntelligence(Bot bot, BotReflex reflex, BotBrain brain) {
        this.bot = bot;
        this.reflex = reflex;
        this.brain = brain;
    }

    /**
     * Processes this bot’s AI for the current game tick.
     * <p>
     * The execution pipeline is:
     * <ul>
     *     <li>Run {@link BotReflex#process(Bot)} to check for instinctive reactions.</li>
     *     <li>If no reflex triggered, and no {@link BotScript} is running, run {@link BotBrain#process(Bot)}.</li>
     *     <li>Execute the returned {@link BotCoordinator}, if one was chosen.</li>
     * </ul>
     */
    public void process() {
        // First process any instincts our bot has.
        if (reflex.process(bot)) {
            // Okay, no instinctual actions were triggered.
            BotScript<?> script = bot.getScriptStack().current();
            if (script != null && script.isRunning()) {
                // But we're currently doing something. So don't proceed.
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
}
