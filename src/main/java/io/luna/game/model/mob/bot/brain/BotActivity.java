package io.luna.game.model.mob.bot.brain;

import api.bot.coordinators.TrainingSkillsCoordinator;
import io.luna.game.model.mob.bot.Bot;

/**
 * Represents the high-level activity categories that a {@link Bot} can perform. This enum is the entry point
 * for the bot’s “behavioral mode,” controlling what kind of actions or sub-scripts the bot prioritizes at runtime.
 * <p>
 * Each activity type optionally defines a {@link BotCoordinator} responsible for orchestrating its internal logic
 * and sub-behaviors. New activity types can be added to expand the bot’s behavioral repertoire (e.g., “BOSSING” or
 * “QUESTING”), each linked to a dedicated coordinator implementation.
 *
 * @author lare96
 */
public enum BotActivity {

    /**
     * General non-combat skill training (e.g., Woodcutting, Mining, Fishing).
     */
    TRAINING_SKILLS(TrainingSkillsCoordinator.INSTANCE),

    /**
     * Combat training, such as fighting NPCs to improve Attack, Strength, or Defence.
     */
    TRAINING_COMBAT(null),

    /**
     * Money-making through non-combat skills (e.g., crafting items, gathering resources for profit).
     */
    PROFIT_SKILLS(null),

    /**
     * Money-making through combat (e.g., killing profitable monsters, farming drops).
     */
    PROFIT_COMBAT(null),

    /**
     * Player-versus-player combat, including wilderness fights and PK strategies.
     */
    PKING(null),

    /**
     * Economic interactions, such as trading with other bots or players.
     */
    TRADING(null),

    /**
     * Social activities like chatting, emotes, or community events.
     */
    SOCIALIZING(null),

    /**
     * Participation in structured mini-games or cooperative content.
     */
    MINIGAMES(null);

    /**
     * The coordinator that manages this activity, or null if unassigned.
     */
    private final BotCoordinator coordinator;

    /**
     * Creates a new {@link BotActivity}.
     *
     * @param coordinator The coordinator that manages this activity, or null if unassigned.
     */
    BotActivity(BotCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * @return The coordinator that manages this activity, or null if unassigned.
     */
    public BotCoordinator getCoordinator() {
        return coordinator;
    }
}