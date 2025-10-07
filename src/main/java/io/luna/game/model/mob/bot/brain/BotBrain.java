package io.luna.game.model.mob.bot.brain;

import api.bot.BotScript;
import io.luna.game.model.mob.bot.Bot;
import io.luna.util.RandomUtils;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

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
     * A functional interface representing a high-level behavior controller for a {@link Bot}.
     * <p>
     * Each {@code BotCoordinator} encapsulates the logic required to manage a specific behavioral domain,
     * such as training, combat, or social activities. Coordinators act as the “executive layer” between the
     * {@link BotBrain} and the lower-level {@link BotScript} routines.
     *
     * @author lare96
     * @see BotActivity
     */
    public interface BotCoordinator extends Consumer<Bot> {
    }

    /**
     * Processes the bot’s reasoning for this game tick.
     *
     * @param bot The bot.
     * @return A {@link BotCoordinator} to execute this tick, or {@code null} if no coordinator was chosen.
     */
    public BotCoordinator process(Bot bot) {
         /* TODO Logout desirability should be checked here, right before trivial decision making. If we're ready, make
             logout a priority interrupt what we're doing. Once the current script stops, the logout script will run
             and no other processes but instinctual actions can interrupt it. Something like
             if(isLogoutReady()) {
                if(!scriptStack.has(BotLogoutScript::class)) {
                    // Push a logout script onto the stack if we don't already have one.
                    scriptStack.softPushHead(new BotLogoutScript(bot))
                }
                // Decision making/regular activities won't be selected.
                return null;
             }
           */

        /* TODO We can do the same thing here for bot 'needs' or 'desires.' If a bot needs <x> item or something, run
            'BotRequestScript' (bad name) and short-circuit here until completion. */

        BotActivity activity = selectActivity(bot.getPreferences().getActivities());
        return activity.getCoordinator();
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
