package io.luna.game.model.mob.bot.brain;

import api.bot.BotScript;
import api.bot.scripts.IdleBotScript;
import api.bot.scripts.IdleBotScript.Companion.IdleData;
import api.bot.scripts.IdleBotScript.State;
import io.luna.game.model.mob.bot.Bot;
import io.luna.util.RandomUtils;

import java.util.Map;
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
     * A fallback coordinator that makes the bot stand idle for a short period of time.
     * <p>
     * Social bots idle for slightly longer, which gives them more time to appear present in the world, chat, or linger
     * between larger activities.
     */
    static class IdleCoordinator implements BotCoordinator {

        /**
         * The extra idle duration added to bots with a social personality.
         */
        private static final int SOCIAL_DURATION_BOOST = 20;

        @Override
        public void accept(Bot bot) {
            int min = 10 + (bot.getPersonality().isSocial() ? SOCIAL_DURATION_BOOST : 0);
            int max = 60 + (bot.getPersonality().isSocial() ? SOCIAL_DURATION_BOOST : 0);
            bot.getScriptStack().push(new IdleBotScript(bot, new IdleData(RandomUtils.inclusive(min, max),
                    State.STANDING)));
        }
    }

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
        // TODO@0.5.0 Check if bot needs any items within its preferences before selecting.
        // TODO@0.5.0 Check if bot is ready to logout before selecting. If bot is social it will stay on until its task
        //  is done, otherwise it'll be interrupted.
        BotActivity activity = selectActivity(bot.getPreferences().getActivities());
        return activity.getCoordinator() == null ? new IdleCoordinator() : activity.getCoordinator();
    }

    /**
     * Selects a weighted activity from the provided activity preference map.
     * <p>
     * If the weighted roll fails to select an activity, this falls back to a random activity from all registered bot
     * activities.
     *
     * @param activities The weighted activity preferences to roll against.
     * @return The selected activity.
     */
    private BotActivity selectActivity(Map<BotActivity, Double> activities) {
        /*
         * TODO@0.5.0 Add more bias:
         * - Which activities have we not done recently?
         * - What context signals are active?
         * - Does the bot have everything needed for this activity?
         * - Should activity weights decay or use cooldowns?
         */
        BotActivity activity = RandomUtils.weightedRoll(activities);
        if (activity == null) {
            activity = RandomUtils.random(BotActivity.ALL);
        }
        return activity;
    }
}