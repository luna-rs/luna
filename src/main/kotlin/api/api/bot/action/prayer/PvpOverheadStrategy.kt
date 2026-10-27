package api.bot.action.prayer

import api.combat.death.PlayerDeathItems
import api.predef.prayer
import engine.combat.prayer.CombatPrayer
import io.luna.Luna
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PrayerIcon
import io.luna.game.model.mob.SkullIcon
import io.luna.game.model.mob.bot.Bot
import kotlin.math.roundToInt

/**
 * Chooses occasional tactical overheads for an intelligent bot fighting a player.
 * Protection remains the caller's fallback: all six overheads share one native prayer group.
 * Decisions use the last observed hit from this opponent, visible overheads, carried food and current own stats;
 * they do not inspect an opponent's hidden Prayer points or future attack rolls.
 *
 * Smite trades protection for pressure only at seventy percent health or more, with at least thirty percent Prayer,
 * a confident personality and a last observed hit no larger than ten percent of maximum health. It reacts after
 * one to six ticks according to intelligence/dexterity. Emergencies do not wait for that offensive reaction window.
 * Redemption requires no food and a repeated observed hit that would leave the bot alive below ten percent health.
 * It is rejected if spending all Prayer would sacrifice an additional tradeable item protected by Protect Item.
 * Retribution is a last resort for a confident bot still fighting an adjacent opponent whose observed hit could kill
 * it at fifteen percent health or less. These choices never cancel eating, travel or an escape.
 *
 * @property bot The bot whose fight owns this temporary decision state.
 * @author lare96
 */
class PvpOverheadStrategy(private val bot: Bot) {

    companion object {
        /**
         * Tactical overheads owned by this policy, excluding the caller's protection prayers and Protect Item.
         */
        private val SPECIAL = setOf(CombatPrayer.SMITE, CombatPrayer.REDEMPTION, CombatPrayer.RETRIBUTION)
    }

    /**
     * Opponent for a pending offensive decision; a new opponent cannot inherit the reaction window.
     */
    private var pendingOpponent: Player? = null

    /**
     * World tick at which the pending Smite decision may replace protection.
     */
    private var smiteAt: Long? = null

    /**
     * Returns one eligible tactical overhead, or null to retain the caller's ordinary protection/saving policy.
     * Eligibility is recomputed every check, including while waiting to Smite. Critical survival choices can use the
     * last prayer point; ordinary offensive choices respect the caller's reserve hysteresis.
     *
     * @param opponent The owning combat script's current player focus.
     * @param savingPrayer Whether the caller is conserving its reserve until a sufficient restore.
     * @param retreating Whether the bot is escaping rather than trying to win this fight.
     */
    fun select(opponent: Player, savingPrayer: Boolean, retreating: Boolean): CombatPrayer? {
        if (bot.state != EntityState.ACTIVE || bot.health <= 0 || bot.prayer.level <= 0 || bot.preferences.isSkiller ||
            !bot.personality.isIntelligent || opponent.state != EntityState.ACTIVE || !opponent.isAlive ||
            !opponent.isViewableFrom(bot)) {
            resetDecision()
            return null
        }
        val maximumHp = bot.totalHealth.coerceAtLeast(1)
        val health = bot.health
        val hit = bot.combat.lastDamageReceived?.takeIf { it.attacker === opponent }?.rawAmount?.coerceAtLeast(0) ?: 0
        val noFood = !bot.actionHandler.inventory.hasAnyFood()
        fun usable(prayer: CombatPrayer) = bot.prayer.staticLevel >= prayer.level

        if (noFood && health.toLong() * 4 <= maximumHp) {
            val afterHit = health - hit
            if (usable(CombatPrayer.REDEMPTION) && hit > 0 && afterHit > 0 &&
                afterHit.toLong() * 10 < maximumHp && !sacrificesProtectedItem()) {
                resetDecision()
                return CombatPrayer.REDEMPTION
            }
            if (!retreating && usable(CombatPrayer.RETRIBUTION) && bot.personality.confidence >= 0.7 &&
                health.toLong() * 100 <= maximumHp.toLong() * 15 && hit >= health &&
                bot.isWithinDistance(opponent, 1)) {
                resetDecision()
                return CombatPrayer.RETRIBUTION
            }
        }

        val comfortable = !savingPrayer && !retreating && usable(CombatPrayer.SMITE) &&
            bot.personality.confidence >= 0.5 && health.toLong() * 10 >= maximumHp.toLong() * 7 &&
            bot.prayer.level >= maxOf(5, (bot.prayer.staticLevel * 0.3).roundToInt()) &&
            opponent.prayerIcon != PrayerIcon.NONE && hit > 0 && hit.toLong() * 10 <= maximumHp
        if (!comfortable) {
            resetDecision()
            return null
        }
        if (bot.combat.prayers.isActive(CombatPrayer.SMITE)) return CombatPrayer.SMITE
        if (pendingOpponent !== opponent || smiteAt == null) {
            pendingOpponent = opponent
            val intelligence = bot.personality.intelligence.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.5
            val dexterity = bot.personality.dexterity.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.5
            val ticks = (1 + (5 * (1 - (intelligence + dexterity) / 2)).roundToInt()).coerceIn(1, 6)
            smiteAt = bot.world.currentTick + ticks
        }
        return if (bot.world.currentTick >= smiteAt!!) CombatPrayer.SMITE else null
    }

    /**
     * Conservatively preserves the extra item under the default death rules. Administrative immunity permits
     * Redemption; an unrecognized safe minigame keeps this conservative rule rather than guessing its death policy.
     * The selection is read-only and uses the same native retention ordering as the death handler.
     */
    private fun sacrificesProtectedItem(): Boolean {
        if (!PlayerDeathItems.losesItems(bot, Luna.settings().game().betaMode())) return false
        val base = if (bot.skullIcon == SkullIcon.WHITE) 0 else 3
        return PlayerDeathItems.select(bot.inventory.filterNotNull() + bot.equipment.filterNotNull(), base)
            .lost.isNotEmpty()
    }

    /**
     * Releases obsolete tactical prayers before the caller activates its one selected overhead normally.
     */
    fun deactivateUnused(selected: CombatPrayer?) {
        SPECIAL.filter { it != selected }.forEach { bot.combat.prayers.deactivate(it, true) }
    }

    /**
     * Clears pending decisions when the fight ends; emergency protection is retained while still under attack.
     */
    fun leaveFight() {
        resetDecision()
        if (!bot.combat.inCombat()) deactivateUnused(null)
    }

    /**
     * Discards an offensive reaction window when its observed conditions cease to apply.
     */
    private fun resetDecision() {
        pendingOpponent = null
        smiteAt = null
    }
}
