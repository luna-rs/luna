package io.luna.game.model.mob.bot.brain;

import io.luna.game.model.mob.PlayerAppearance.DesignPlayerInterface;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.inter.AbstractInterfaceSet;
import io.luna.game.model.mob.inter.DialogueInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The low-level reflex controller for {@link Bot} entities.
 * <p>
 * {@link BotReflex} operates as the “lizard brain” for every bot handling involuntary, automatic reactions that
 * occur without higher-level thought. This includes things like continuing dialogue, closing interfaces, or making
 * instinctual combat decisions.
 *
 * <p>Reflexes run once per server tick, prior to scripted logic in the {@link BotBrain}.
 * If any reflex triggers (returns {@code false}), the bot’s brain will skip its main decision
 * cycle for that tick. This models how reflexive behavior momentarily overrides conscious action.
 *
 * <p>Example behaviors managed here:
 * <ul>
 *   <li>Randomizing appearance when the design interface is open.</li>
 *   <li>Automatically clicking “Continue” during dialogues.</li>
 *   <li>Evading or countering in combat under low health conditions.</li>
 * </ul>
 *
 * <p>Custom reflexes can be registered dynamically through {@link #addInstinct(BotInstinct)}.
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
    private final List<BotInstinct> instinctList = new ArrayList<>();

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
        // Handle automatic character design if the interface is open.
        AbstractInterfaceSet interfaces = bot.getInterfaces();
        if (interfaces.standardTo(DesignPlayerInterface.class).isPresent()) {
            bot.getOutput().sendCharacterDesignSelection();
            bot.log("Reflex triggered: select appearance.");
            return false;
        }

        // Automatically progress dialogue interfaces.
        if (interfaces.standardTo(DialogueInterface.class).isPresent()) {
            bot.getOutput().sendContinueDialogue();
            bot.log("Reflex triggeredX: continue dialogue.");
            return false;
        }

        /* TODO Handle how the bot should respond to combat here.
            This may include running away, fighting back, or switching targets maybe?
            Stuff to consider:
            - Bot’s current health and combat level.
            - Opponent type (player, NPC, or another bot).
            - Current equipment and available resources.
            - Nearby allies or escape routes.
            - Bot personality and current mood.
            Potentially delegate this logic to a CombatReflex or CombatBotScript. */

        // Execute all additional registered instincts.
        for (BotInstinct instinct : instinctList) {
            if (!instinct.apply(bot)) {
                bot.log("Dynamic reflex [" + instinct.getClass().getName() + "] triggered.");
                return false;
            }
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
        instinctList.add(instinct);
    }
}
