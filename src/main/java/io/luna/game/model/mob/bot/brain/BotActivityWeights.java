package io.luna.game.model.mob.bot.brain;

import api.bot.action.BotCombatActionHandler;
import engine.bot.coordinator.PkingCoordinator;
import engine.bot.coordinator.skill.SkillingCoordinator;
import io.luna.game.model.item.shop.Currency;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.bot.Bot;

import java.util.EnumMap;
import java.util.Map;

import static api.predef.ext.BotPreferenceExtKt.totalItemAmount;

/**
 * Adjusts activity interests as an account progresses without changing its saved personality or build.
 * Combat progression raises PK interest; combat or skilling progression raises minigame interest.
 * Progression makes only a small adjustment: training retains at least ninety-five percent of its weight,
 * while PKing and minigames gain at most ten percent. Exhausted training activities are excluded.
 *
 * @author lare96
 */
public final class BotActivityWeights {

    /**
     * Bots below this total coin balance favor activities that can produce spendable coins.
     */
    public static final int STARTER_COIN_TARGET = 10_000;

    /**
     * Raises profit skilling's share while a bot is building its first coin reserve.
     */
    private static final double STARTER_PROFIT_SKILLS_MULTIPLIER = 6.0;

    private BotActivityWeights() {
    }

    /**
     * Checks coins across all of the bot's tracked item containers.
     */
    public static boolean needsStarterCoins(Bot bot) {
        return totalItemAmount(bot, Currency.COINS.getId()) < STARTER_COIN_TARGET;
    }

    /**
     * Returns mutable, eligible decision weights. Zero, negative and non-finite saved interests stay disabled.
     * Young accounts retain the existing combat bonuses. Profit interests do not taper with progression.
     */
    public static Map<BotActivity, Double> weights(Bot bot) {
        var weights = new EnumMap<BotActivity, Double>(BotActivity.class);
        var preferences = bot.getPreferences();
        double combatProgress = Math.clamp((bot.getCombatLevel() - 3.0) / 123.0, 0.0, 1.0);
        double skillProgress = Skill.IDS.stream().filter(id -> !Skill.isCombatSkill(id))
                .mapToDouble(id -> Math.clamp((bot.skill(id).getStaticLevel() - 1.0) / 98.0, 0.0, 1.0))
                .average().orElse(0.0);
        double minigameProgress = Math.max(combatProgress, skillProgress);
        boolean young = bot.getTimePlayed().toDays() < 2;
        boolean needsCoins = needsStarterCoins(bot);

        preferences.getActivities().forEach((activity, interest) -> {
            if (interest == null || !Double.isFinite(interest) || interest <= 0.0 ||
                    !preferences.canChooseActivity(activity)) return;
            if (activity == BotActivity.TRAINING_SKILLS && !SkillingCoordinator.hasTrainingSkills(bot) ||
                    activity == BotActivity.TRAINING_COMBAT && BotCombatActionHandler.wantedCombatSkills(bot).isEmpty()) return;
            double weight = switch (activity) {
                case TRAINING_SKILLS -> interest * (1.0 - 0.05 * skillProgress);
                case TRAINING_COMBAT -> (interest + (young ? 0.35 : 0.0)) * (1.0 - 0.05 * combatProgress);
                case PROFIT_SKILLS -> interest * (needsCoins ? STARTER_PROFIT_SKILLS_MULTIPLIER : 1.0);
                case PROFIT_COMBAT -> interest + (young ? 0.25 : 0.0);
                case PKING -> PkingCoordinator.INSTANCE.activityWeight(bot) * (1.0 + 0.10 * combatProgress);
                case MINIGAMES -> interest * (1.0 + 0.10 * minigameProgress);
                default -> interest;
            };
            if (Double.isFinite(weight) && weight > 0.0) weights.put(activity, weight);
        });
        return weights;
    }
}
