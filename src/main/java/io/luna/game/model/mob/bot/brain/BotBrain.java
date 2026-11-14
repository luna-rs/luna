package io.luna.game.model.mob.bot.brain;

import api.bot.script.BotScript;
import api.bot.action.BotBankSpace;
import engine.bot.coordinator.BankResourceCoordinator;
import engine.bot.coordinator.GeneralProfitCoordinator;
import engine.bot.coordinator.ScavengingCoordinator;
import engine.bot.coordinator.RuneRunningCoordinator;
import game.bot.scripts.CannonBotSupport;
import game.bot.scripts.RecoverCannonBotScript;
import game.bot.scripts.BankCleanupBotScript;
import game.bot.scripts.BankRecoveryWaitBotScript;
import game.bot.scripts.skills.FarmingReturnBotScript;
import io.luna.game.model.mob.bot.Bot;
import io.luna.util.RandomUtils;

import java.util.function.Consumer;
import java.util.concurrent.ThreadLocalRandom;

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
     * Next optional starter profit check for this bot's brain. A failed roll also waits, so idle bots
     * cannot turn a per-visit chance into a guaranteed dispatch over several consecutive ticks.
     */
    private long nextStarterProfitAt;

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
        if (CannonBotSupport.needsRecovery(bot)) {
            return owner -> owner.getScriptStack().push(new RecoverCannonBotScript(owner));
        }
        if (ScavengingCoordinator.INSTANCE.canDispatch(bot)) {
            return ScavengingCoordinator.INSTANCE;
        }
        if (bot.getPreferences().getRuneRunning().isIntroductory() && RuneRunningCoordinator.INSTANCE.canDispatch(bot)) {
            return RuneRunningCoordinator.INSTANCE;
        }
        BotScript resourceRelief = BankResourceCoordinator.select(bot);
        if (resourceRelief != null) {
            return owner -> owner.getScriptStack().push(resourceRelief);
        }
        if (BotBankSpace.needsMaintenance(bot)) {
            return owner -> owner.getScriptStack().push(new BankCleanupBotScript(owner));
        }
        if (BotBankSpace.recoveryDeferred(bot)) {
            return owner -> owner.getScriptStack().push(
                    new BankRecoveryWaitBotScript(owner, System.currentTimeMillis() + 120_000L));
        }
        FarmingReturnBotScript farmingReturn = FarmingReturnBotScript.poll(bot);
        if (farmingReturn != null) {
            return owner -> owner.getScriptStack().pushTail(farmingReturn);
        }
        BotScript runeCrafter = RuneRunningCoordinator.INSTANCE.introductoryCrafterScript(bot);
        if (runeCrafter != null) {
            return owner -> owner.getScriptStack().push(runeCrafter);
        }
        long now = System.currentTimeMillis();
        if (bot.getCombatLevel() <= 10 && bot.getTimePlayed().toHours() < 48 &&
                now >= nextStarterProfitAt && bot.getPreferences().canChooseActivity(BotActivity.GENERAL_PROFIT)) {
            nextStarterProfitAt = now + 45L * 60_000;
            Double interest = bot.getPreferences().getActivities().get(BotActivity.GENERAL_PROFIT);
            if (interest != null && Double.isFinite(interest) && interest > 0.0 &&
                    ThreadLocalRandom.current().nextDouble() < 0.25) {
                BotScript starterProfit = GeneralProfitCoordinator.INSTANCE.starterVials(bot);
                if (starterProfit != null) {
                    return owner -> owner.getScriptStack().push(starterProfit);
                }
            }
        }
        var map = BotActivityWeights.weights(bot);
        BotActivity activity = RandomUtils.weightedRoll(map);
        if (activity == null) {
            return BotActivity.SOCIALIZING.getCoordinator();
        }
        return activity.getCoordinator();
    }
}
