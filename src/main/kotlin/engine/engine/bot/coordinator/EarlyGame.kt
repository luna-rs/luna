package engine.bot.coordinator

import io.luna.game.model.mob.bot.Bot

/**
 * Admits a bot to starter activities while it remains below all three progression cutoffs.
 */
internal fun Bot.isEarlyGame(): Boolean =
    combatLevel < 30 && skills.sumOf { it.staticLevel } < 300 && timePlayed.toHours() < 48
