package engine.bot.coordinator

import api.bot.action.PkClanPolicy
import api.bot.script.BotScript.ActionSafety
import api.bot.script.BotScript
import game.bot.scripts.combat.PkBotScript
import io.luna.game.model.EntityState
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Dispatches saved PK identities to prepare or procure an affordable, effective PK kit before departure.
 * Clan cape replenishment is projected without creating an item here. An ordinary clan outing needs another
 * assigned clanmate online; a confidence-selected solo outing may depart alone. Banking and live checks belong
 * to the script, so coordinator eligibility never authorizes an attack by itself.
 *
 * @author lare96
 */
object PkingCoordinator : BotCoordinator {
    override fun accept(bot: Bot) {
        getScript(bot)?.let { bot.scriptStack.push(it) }
    }

    /**
     * Makes positive PK interests three times as competitive with long skilling and NPC sessions.
     * Dedicated PKers receive at least weight three despite legacy randomized preferences. Explicit zero,
     * invalid weights, skillers, introductory scavengers and accounts without a PK identity remain excluded.
     * This is a decision weight only; clan, supplies, safety and risk still gate departure.
     */
    fun activityWeight(bot: Bot): Double {
        val weight = bot.preferences.activities[BotActivity.PKING] ?: return 0.0
        if (!weight.isFinite() || weight <= 0.0 || bot.preferences.isSkiller || bot.preferences.pk == null ||
            !bot.preferences.canChooseActivity(BotActivity.PKING)) return 0.0
        val affinity = weight.coerceAtMost(1.0)
        return if (bot.preferences.isPker) maxOf(0.75, affinity) * 4.0 else affinity * 3.0
    }

    /**
     * Chooses one to eight hours without changing the saved preference. PK affinity contributes half the
     * commitment, confidence a quarter, and the dedicated PKer personality the remaining quarter.
     * Restored sessions retain their saved remaining time rather than receiving a fresh full duration.
     */
    internal fun sessionDuration(bot: Bot): Duration {
        fun normalized(value: Double) = if (value.isFinite()) value.coerceIn(0.0, 1.0) else 0.5
        val affinity = normalized(bot.preferences.activities[BotActivity.PKING] ?: 0.0)
        val confidence = normalized(bot.personality.confidence)
        val dedication = if (bot.preferences.isPker) 1.0 else 0.0
        val commitment = (2.0 * affinity + confidence + dedication) / 4.0
        return (60 + (420 * commitment).roundToInt()).minutes
    }

    /**
     * Preserves permanent purpose/styles/build and declines duplicate or unsafe sessions.
     * Missing supplies enter the preparation stage, where ordinary acquisition and funding can address them.
     */
    internal fun getScript(bot: Bot): BotScript? {
        if (bot.state != EntityState.ACTIVE || bot.preferences.isSkiller || bot.preferences.pk == null ||
            !bot.preferences.canChooseActivity(BotActivity.PKING) || !ActionSafety.NON_COMBAT.allows(bot) ||
            bot.scriptStack.size() > 0) return null
        val solo = PkClanPolicy.chooseSolo(bot)
        val clan = PkClanPolicy(bot)
        if (clan.clanOuting && !solo && clan.onlineMembers(activePkOnly = false).isEmpty()) return null
        return PkBotScript(bot, sessionDuration(bot), solo)
    }
}
