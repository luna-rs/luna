package io.luna.game.model.mob.bot.brain;

import api.bot.script.ReflexBotScript;
import game.bot.scripts.combat.CombatBotScript;
import game.bot.scripts.combat.CombatBotScript.InitialState;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.block.PlayerAppearance;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.dialogue.NpcDialogue;
import io.luna.game.model.mob.dialogue.PlayerDialogue;
import io.luna.game.model.mob.dialogue.TextDialogue;

import java.util.ArrayList;
import java.util.List;

/**
 * Controls automatic low-level reactions for a {@link Bot}.
 * <p>
 * This is the bot's reflex layer. It handles immediate behaviour that should run before the normal {@link BotBrain}
 * decision cycle, such as continuing dialogue, completing character design, starting combat response scripts, or running
 * registered {@link ReflexBotScript} instances.
 * <p>
 * If a reflex triggers, {@link #process(Bot)} returns {@code false}. This tells the brain to skip its normal decision
 * cycle for the current tick so the reflex action can take priority.
 *
 * @author lare96
 */
public final class BotReflex {

    /**
     * The registered custom reflex scripts for this bot.
     * <p>
     * Reflex scripts are checked in insertion order. The first script whose {@link ReflexBotScript#shouldReact()} method
     * returns {@code true} becomes the active reflex.
     */
    private final List<ReflexBotScript> reflexes = new ArrayList<>();

    /**
     * The reflex script currently running, or {@code null} if no custom reflex is active.
     */
    private ReflexBotScript activeReflex;

    /**
     * Whether automatic combat responses are disabled.
     * <p>
     * When enabled, combat scripts or other high-level scripts are responsible for deciding how the bot responds to
     * combat instead of this reflex controller.
     */
    private boolean disableCombatReflex;

    /**
     * Processes this bot's reflex layer for one tick.
     * <p>
     * Active custom reflex scripts take priority. If one is already running, all other reflex checks are skipped until it
     * finishes. If no custom reflex is active, registered reflex scripts are checked first, followed by built-in reflexes
     * for character design, dialogue continuation, and combat response.
     *
     * @param bot The bot being processed.
     *
     * @return {@code true} if no reflex triggered and normal brain logic may continue, or {@code false} if reflex logic
     * took over this tick.
     */
    public boolean process(Bot bot) {
        if (activeReflex != null) {
            if (activeReflex.isRunning()) {
                // Reflex script is still running; skip all other processing.
                return false;
            }
            // No longer running; reset active script.
            activeReflex.stop();
            activeReflex = null;
        } else {
            for (ReflexBotScript script : reflexes) {
                if (script.shouldReact()) {
                    // Reflex script was triggered, start it.
                    activeReflex = script;
                    activeReflex.start();
                    return false;
                }
            }
        }

        // Handle automatic character design if the interface is open.
        if (bot.getOverlays().contains(PlayerAppearance.DesignPlayerInterface.class)) {
            bot.getOutput().sendCharacterDesignSelection();
            bot.log("Reflex [select appearance] triggered.");
            return false;
        }

        // Automatically progress dialogue interfaces.
        if (bot.getOverlays().contains(NpcDialogue.class) || bot.getOverlays().contains(PlayerDialogue.class) ||
                bot.getOverlays().contains(TextDialogue.class)) {
            bot.getOutput().sendContinueDialogue();
            bot.log("Reflex [continue dialogue] triggered.");
            return false;
        }

        // Automatically respond to combat if we aren't already.
        if (!disableCombatReflex && bot.getCombat().inCombat() && !(bot.getScriptStack().current() instanceof CombatBotScript)) {
            Mob focus = bot.getCombat().getTarget();
            if (focus == null && bot.getCombat().getLastAttackReceived() != null) {
                focus = bot.getCombat().getLastAttackReceived().getAttacker();
            }
            if (focus != null) {
                InitialState response = bot.getEmotions().getCombatResponse(focus);
                bot.getScriptStack().pushHead(new CombatBotScript(bot, focus, response));
                bot.log("Reflex [responding to combat] triggered.");
                return false;
            }
        }

        // No reflexes triggered; continue brain logic.
        return true;
    }

    /**
     * Registers a custom reflex script.
     * <p>
     * Registered reflex scripts are processed in the order they were added. The first script that wants to react becomes
     * the active reflex and blocks normal brain logic until it finishes.
     *
     * @param script The reflex script to register.
     */
    public void add(ReflexBotScript script) {
        reflexes.add(script);
    }

    /**
     * @return {@code true} if automatic combat reflexes are disabled.
     */
    public boolean isDisableCombatReflex() {
        return disableCombatReflex;
    }

    /**
     * Sets whether this bot should skip automatic combat reflexes.
     *
     * @param disableCombatReflex {@code true} to let scripts handle combat response themselves.
     */
    public void setDisableCombatReflex(boolean disableCombatReflex) {
        this.disableCombatReflex = disableCombatReflex;
    }

    /**
     * @return The currently active reflex script, or {@code null} if none is active.
     */
    public ReflexBotScript getActiveReflex() {
        return activeReflex;
    }
}