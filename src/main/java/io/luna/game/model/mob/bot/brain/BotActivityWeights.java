package io.luna.game.model.mob.bot.brain;

import api.bot.action.BotCombatActionHandler;
import engine.bot.coordinator.PkingCoordinator;
import engine.bot.coordinator.skill.SkillingCoordinator;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.bot.Bot;

import java.util.EnumMap;
import java.util.Map;

/**
 * Adjusts activity interests as an account progresses without changing its saved personality or build.
 * Combat progression raises PK interest; combat or skilling progression raises minigame interest.
 * Progression makes only a small adjustment: training retains at least ninety-five percent of its weight,
 * while PKing and minigames gain at most ten percent. Exhausted training activities are excluded.
 *
 * @author lare96
 */
public final class BotActivityWeights {

    private BotActivityWeights() {
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

        preferences.getActivities().forEach((activity, interest) -> {
            if (interest == null || !Double.isFinite(interest) || interest <= 0.0 ||
                    !preferences.canChooseActivity(activity)) return;
            if (activity == BotActivity.TRAINING_SKILLS && !SkillingCoordinator.hasTrainingSkills(bot) ||
                    activity == BotActivity.TRAINING_COMBAT && BotCombatActionHandler.wantedCombatSkills(bot).isEmpty()) return;
            double weight = switch (activity) {
                case TRAINING_SKILLS -> interest * (1.0 - 0.05 * skillProgress);
                case TRAINING_COMBAT -> (interest + (young ? 0.35 : 0.0)) * (1.0 - 0.05 * combatProgress);
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
