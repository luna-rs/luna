package io.luna.game.model.mob.bot.brain;

import game.bot.scripts.combat.CombatBotScript;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.block.PlayerAppearance;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.dialogue.DialogueInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.util.Objects.requireNonNullElse;

/**
 * The low-level reflex controller for {@link Bot} entities.
 * <p>
 * {@link BotReflex} operates as the “lizard brain” for every bot handling involuntary, automatic reactions that
 * occur without higher-level thought. This includes things like continuing dialogue, closing interfaces, or making
 * instinctual combat decisions.
 * <p>
 * Reflexes run once per server tick, prior to scripted logic in the {@link BotBrain}. If any reflex triggers
 * (returns {@code false}), the bot’s brain will skip its main decision cycle for that tick. This models how
 * reflexive behavior momentarily overrides conscious action.
 * <p>
 * Example behaviors managed here:
 * <ul>
 *   <li>Randomizing appearance when the design interface is open.</li>
 *   <li>Automatically clicking “Continue” during dialogues.</li>
 *   <li>Evading or countering in combat under low health conditions.</li>
 * </ul>
 * Custom reflexes can be registered dynamically through {@link #addInstinct(BotInstinct)}.
 *
 * @author lare96
 */
public class BotReflex {

    /**
     * Represents a single reflex or involuntary instinct that a bot can perform.
     * <p>
     * Returning {@code false} means this reflex triggered an action that cancels the bot's thinking phase for this
     * cycle, while {@code true} means no action was taken and the {@link BotBrain} can still run.
     */
    public interface BotInstinct extends Function<Bot, Boolean> {
    }

    /**
     * The list of registered reflexes for this bot.
     */
    private final List<BotInstinct> reflexes = new ArrayList<>();

    /**
     * Disables the combat reflex so scripts can handle how the bot responds to combat.
     */
    private boolean disableCombatReflex;

    /**
     * Executes all reflex checks for the given bot.
     * <p>
     * If any reflex triggers (e.g. an interface is open or a dialogue is ongoing), the bot’s main {@link BotBrain}
     * logic will be paused until the reflex resolves.
     *
     * @param bot The bot.
     * @return {@code false} if a reflex action occurred, {@code true} if the bot is free to proceed with
     * its regular logic.
     */
    public boolean process(Bot bot) {
        // Execute all registered instincts.
        for (BotInstinct instinct : reflexes) {
            if (!instinct.apply(bot)) {
                Class<?> type = instinct.getClass();
                String name = type.isAnonymousClass() ? "anonymous" : instinct.getClass().getName();
                bot.log("Dynamic reflex [" + name + "] triggered.");
                return false;
            }
        }

        // Handle automatic character design if the interface is open.
        if (bot.getOverlays().contains(PlayerAppearance.DesignPlayerInterface.class)) {
            bot.getOutput().sendCharacterDesignSelection();
            bot.log("Reflex [select appearance] triggered.");
            return false;
        }

        // Automatically progress dialogue interfaces.
        if (bot.getOverlays().contains(DialogueInterface.class)) {
            bot.getOutput().sendContinueDialogue();
            bot.log("Reflex [continue dialogue] triggered.");
            return false;
        }

        // Automatically respond to combat if we aren't already.
        if (!disableCombatReflex && bot.getCombat().inCombat() && !(bot.getScriptStack().current() instanceof CombatBotScript)) {
            Mob focus = requireNonNullElse(bot.getCombat().getTarget(), bot.getCombat().getAutoRetaliateTarget());
            bot.getScriptStack().pushHead(new CombatBotScript(bot, focus));
            bot.log("Reflex [responding to combat] triggered.");
            return false;
        }

        // No reflexes triggered; continue brain logic
        return true;
    }

    /**
     * Registers a new {@link BotInstinct}. They are processed in the order they were added.
     *
     * @param instinct The instinct function to add.
     */
    public void addInstinct(BotInstinct instinct) {
        reflexes.add(instinct);
    }

    /**
     * Disables the combat reflex tracking, meaning the bot will <strong>not</strong> naturally respond to combat.
     * <p>
     * Scripts will have to define bot behaviour when attacked (or bots will do nothing).
     */
    public void disableCombatReflex() {
        disableCombatReflex = true;
    }

    /**
     * Enables the combat reflex tracking, meaning the bot will naturally respond to combat.
     */
    public void enableCombatReflex() {
        disableCombatReflex = false;
    }
}
