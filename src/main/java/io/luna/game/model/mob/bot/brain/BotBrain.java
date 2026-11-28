package io.luna.game.model.mob.bot.brain;

import api.bot.action.FoodSupplyAcquisition;
import api.bot.script.BotGoals;
import api.bot.script.BotScript;
import api.bot.action.BotBankSpace;
import engine.bot.coordinator.BankResourceCoordinator;
import engine.bot.coordinator.GeneralProfitCoordinator;
import engine.bot.coordinator.ScavengingCoordinator;
import engine.bot.coordinator.RuneRunningCoordinator;
import engine.bot.coordinator.PkCoverage;
import engine.bot.economy.BotProductionMetrics;
import engine.bot.economy.BotProductionMetrics.GeneralProfitStart;
import game.bot.scripts.CannonBotSupport;
import game.bot.scripts.RecoverCannonBotScript;
import game.bot.scripts.BankCleanupBotScript;
import game.bot.scripts.BankRecoveryWaitBotScript;
import game.bot.scripts.BlastFurnaceBotScript;
import game.bot.scripts.skills.FarmingReturnBotScript;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.memory.BotDiaryMemory;
import io.luna.util.RandomUtils;

import java.util.function.Consumer;
import java.util.concurrent.ThreadLocalRandom;

import static api.bot.script.BotDiaryKt.standing;

/**
 * The high-level decision-making center for a {@link Bot}.
 * <p>
 * The {@code BotBrain} decides which activity a bot should pursue based on its current state, needs, and personality
 * profile. Once an activity is selected, the matching {@link BotCoordinator} is used to push or manage the lower-level
 * script logic that performs the actual behavior.
 * <p>
 * This class should remain a lightweight decision layer. It should inspect the bot, choose the next behavior domain,
 * and delegate execution to coordinators or scripts instead of performing detailed gameplay logic directly.
 * <p>
 * Each activity the brain chooses is a session in the bot's {@link BotDiaryMemory diary}, from the choice until the
 * brain is next asked for work. A session that closes tells the bot's outlook how it left the bot's wealth. Each
 * time the brain is asked, the bot's goals are checked and set where it has none.
 *
 * @author lare96
 */
public class BotBrain {

    /**
     * Marks a brain with no choice waiting for its pause.
     */
    private static final long NO_CHOICE = Long.MIN_VALUE;

    /**
     * Next optional starter profit check for this bot's brain. A failed roll also waits, so idle bots
     * cannot turn a per-visit chance into a guaranteed dispatch over several consecutive ticks.
     */
    private long nextStarterProfitAt;

    /**
     * Whether this brain has been asked for work since its bot logged in.
     */
    private boolean asked;

    /**
     * The world tick of the last choice while the pause that follows it has not started, otherwise
     * {@link #NO_CHOICE}.
     */
    private long choiceTick = NO_CHOICE;

    /**
     * The world tick from which this bot may choose again. Kept in memory only.
     */
    private long nextDecisionTick;

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
     * @return The coordinator that should run for this decision cycle, or {@code null} while the bot is still
     * making up its mind.
     */
    public BotCoordinator process(Bot bot) {
        // The brain is only asked while the script stack is empty, so whatever the last choice started is over.
        BotDiaryMemory diary = bot.getMemory().getDiary();
        BotDiaryMemory.Closed closed = diary.isOpen() ? diary.end(standing(bot)) : null;
        if (closed != null) {
            bot.getMemory().getOutlook().sessionEnded(closed.wealth());
        }
        if (closed != null && !closed.started() && closed.jobSelected()) {
            BotProductionMetrics.recordGeneralProfitStart(GeneralProfitStart.SELECTED_JOB_EXITED_EARLY);
        }
        BotGoals.review(bot, closed);
        if (stillDeciding(bot)) {
            return null;
        }
        if (CannonBotSupport.needsRecovery(bot)) {
            return owner -> owner.getScriptStack().push(new RecoverCannonBotScript(owner));
        }
        if (ScavengingCoordinator.INSTANCE.canDispatch(bot)) {
            return ScavengingCoordinator.INSTANCE;
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
        BotScript furnaceRepair = BlastFurnaceBotScript.repairCandidate(bot);
        if (furnaceRepair != null) {
            return owner -> owner.getScriptStack().push(furnaceRepair);
        }
        if (RuneRunningCoordinator.INSTANCE.canDispatch(bot)) {
            return RuneRunningCoordinator.INSTANCE;
        }
        BotScript runeCrafter = RuneRunningCoordinator.INSTANCE.idleCrafterScript(bot);
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
                BotScript starterProfit = GeneralProfitCoordinator.INSTANCE.starterOuting(bot);
                if (starterProfit != null) {
                    return session(BotActivity.GENERAL_PROFIT, owner -> owner.getScriptStack().push(starterProfit));
                }
            }
        }
        BotScript coverage = PkCoverage.idleScript(bot);
        if (coverage != null) {
            return session(BotActivity.PKING, owner -> owner.getScriptStack().push(coverage));
        }
        BotScript foodRestock = FoodSupplyAcquisition.idleScript(bot);
        if (foodRestock != null) {
            return owner -> owner.getScriptStack().push(foodRestock);
        }
        var map = BotActivityWeights.weights(bot);
        BotActivity activity = RandomUtils.weightedRoll(map);
        if (activity == null) {
            activity = BotActivity.SOCIALIZING;
        }
        return session(activity, activity.getCoordinator());
    }

    /**
     * Opens a diary session for an activity before its coordinator starts the work.
     *
     * @param activity The activity the bot is about to do.
     * @param coordinator What starts the work, or {@code null} if nothing does.
     * @return A coordinator that does both.
     */
    private static BotCoordinator session(BotActivity activity, BotCoordinator coordinator) {
        return owner -> {
            BotDiaryMemory diary = owner.getMemory().getDiary();
            diary.begin(activity.name(), standing(owner));
            if (coordinator != null) {
                coordinator.accept(owner);
            }
            if (activity == BotActivity.GENERAL_PROFIT) {
                if (owner.getScriptStack().size() == 0) {
                    BotProductionMetrics.recordGeneralProfitStart(GeneralProfitStart.NO_ELIGIBLE_JOB);
                } else {
                    diary.markJobSelected();
                }
            }
        };
    }

    /**
     * Makes a bot stand for a few seconds before it picks new work. The brain is only asked while the script stack
     * is empty, so the first ask after a choice means that choice started nothing, or the work it started is over.
     * Either way the bot waits before it chooses again. So does a bot whose saved session ends: it is asked for the
     * first time after it has already been busy. Being asked again during a wait never restarts it.
     *
     * @param bot The bot being processed.
     * @return {@code true} if the bot should not choose work on this tick.
     */
    boolean stillDeciding(Bot bot) {
        long tick = bot.getWorld().getCurrentTick();
        boolean busySinceLogin = !asked && bot.getCycles() > 0;
        asked = true;
        if (busySinceLogin || choiceTick != NO_CHOICE && tick != choiceTick) {
            choiceTick = NO_CHOICE;
            nextDecisionTick = tick + pauseTicks(bot.getPersonality().getDexterity(),
                    ThreadLocalRandom.current().nextDouble());
        }
        if (tick < nextDecisionTick) {
            return true;
        }
        choiceTick = tick;
        return false;
    }

    /**
     * How many ticks a bot takes to make up its mind: 3 to 6 for the quickest bots, 8 to 15 for the slowest.
     *
     * @param dexterity The bot's dexterity trait.
     * @param roll A roll from 0 to 1 that spreads the wait around the bot's usual length.
     * @return The ticks to wait.
     */
    static int pauseTicks(double dexterity, double roll) {
        double quickness = Double.isFinite(dexterity) ? Math.clamp(dexterity, 0.0, 1.0) : 0.5;
        double typical = 4.0 + 6.0 * (1.0 - quickness);
        return (int) Math.round(typical * (0.75 + 0.75 * roll));
    }
}
