package io.luna.game.model.mob.bot.brain;

import api.bot.script.BotScript;
import io.luna.game.model.mob.bot.Bot;
import io.luna.util.RandomUtils;

import java.util.HashMap;
import java.util.function.Consumer;

/**
 * The high-level decision-making center for a {@link Bot}.
 * <p>
 * The {@code BotBrain} decides which activity a bot should pursue based on its current state, needs, and personality
 * profile. Once an activity is selected, the matching {@link BotCoordinator} is used to push or manage the lower-level
 * script logic that performs the actual behavior.
 * <p>
 * This class should remain a lightweight decision layer. It should inspect the bot, choose the next behavior domain,
 * and delegate execution to coordinators or scripts instead of performing detailed gameplay logic directly.
 *
 * @author lare96
 */
public class BotBrain {

    /**
     * A high-level behavior controller for a {@link Bot}.
     * <p>
     * Each coordinator manages one broad behavior domain, such as skilling, combat, banking, trading, social activity,
     * or idling. Coordinators act as the executive layer between {@link BotBrain} and lower-level {@link BotScript}
     * implementations.
     *
     * @see BotActivity
     */
    public interface BotCoordinator extends Consumer<Bot> {
    }

    /**
     * Processes the bot's high-level reasoning for this game tick.
     * <p>
     * This selects a weighted activity from the bot's preferences and returns that activity's coordinator.
     * If the selected activity has no coordinator, the bot falls back to an idle coordinator.
     *
     * @param bot The bot being processed.
     * @return The coordinator that should run for this decision cycle.
     */
    public BotCoordinator process(Bot bot) {
        /*
         * TODO@0.5.0 Add more bias:
         * - Check if bot needs any items within its preferences before selecting.
         * - Which activities have we not done recently?
         * - What context signals are active?
         * - Does the bot have everything needed for this activity?
         * - Should activity weights decay or use cooldowns?
         */
        var map = bot.getPreferences().getActivities();
        if (bot.getTimePlayed().toDays() < 2) {
            // New bots have an additional chance to do combat activities.
            map = new HashMap<>(bot.getPreferences().getActivities());
            map.computeIfPresent(BotActivity.TRAINING_COMBAT, (key, value) -> value + 0.35);
            map.computeIfPresent(BotActivity.PROFIT_COMBAT, (key, value) -> value + 0.25);
        }
        BotActivity activity = RandomUtils.weightedRoll(map);
        if (activity == null) {
            activity = RandomUtils.random(BotActivity.ALL);
        }
        return activity.getCoordinator();
    }
}