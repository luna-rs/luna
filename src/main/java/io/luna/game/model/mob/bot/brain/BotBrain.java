package io.luna.game.model.mob.bot.brain;

import api.bot.coordinators.LogoutCoordinator;
import io.luna.game.model.mob.bot.Bot;
import io.luna.util.RandomUtils;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The high-level decision-making center for a {@link Bot}.
 * <p>
 * The {@code BotBrain} determines what activity the bot should pursue based on its current state, needs, and
 * personality profile. Once a decision is made, a corresponding {@link BotCoordinator} is returned to execute
 * domain-specific logic.
 * <p>
 * The brain operates as a stateless decision layer, it should read the bot's state and delegate actual behavior
 * to other modules.
 * <p>
 * <b>Processing order:</b>
 * <ol>
 *     <li>Evaluate short-term needs (scheduled logouts, item needs, gold needs, etc).</li>
 *     <li>Evaluate current {@link BotActivity} preferences.</li>
 *     <li>Select an appropriate {@link BotCoordinator} to run.</li>
 * </ol>
 *
 * @author lare96
 */
public class BotBrain {

    /**
     * Processes the bot’s reasoning for this game tick.
     *
     * @param bot The bot.
     * @return A {@link BotCoordinator} to execute this tick, or {@code null} if no coordinator was chosen.
     */
    public final BotCoordinator process(Bot bot) {
        if (bot.isLogoutScheduled()) {
            return LogoutCoordinator.INSTANCE;
        }
        // TODO Eventually we need an ItemNeedsCoordinator.
        BotActivity activity = selectActivity(bot.getPersonality().getActivities());
        if (activity != null) {
            return activity.getCoordinator();
        }
        return null;
    }

    /**
     * Selects a {@link BotActivity} based on {@code activities}.
     *
     * @param activities The map of activity preferences.
     * @return The selected activity, never {@code null}.
     */
    private BotActivity selectActivity(Map<BotActivity, Double> activities) {
        double totalWeight = activities.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalWeight <= 0.0) {
            return RandomUtils.random(BotActivity.ALL);
        }
        double roll = ThreadLocalRandom.current().nextDouble(0, totalWeight);
        double cumulative = 0.0;
        for (Map.Entry<BotActivity, Double> entry : activities.entrySet()) {
            cumulative += entry.getValue();
            if (roll <= cumulative) {
                return entry.getKey();
            }
        }
        return RandomUtils.random(BotActivity.ALL);
    }
}
