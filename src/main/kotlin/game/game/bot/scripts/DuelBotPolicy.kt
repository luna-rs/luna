package game.bot.scripts

import api.bot.action.CombatLoadout
import api.bot.action.PkRiskBudget
import api.predef.*
import engine.bot.gear.BotGearPurpose
import game.combat.spells.IbanStaff
import game.content.duelArena.DuelRules
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.combat.CombatSpell
import java.math.BigInteger

/**
 * Native duel admission for owned melee, Ranged and Magic outfits, retaining permanent account-build and PK-style
 * limits. Optional GP stakes use one tenth of the existing personality wealth allowance, at most 100,000 GP and one
 * quarter of liquid owned coins. Native escrow and both acceptance screens remain the transaction authority.
 *
 * @author lare96
 */
internal object DuelBotPolicy {
    private val skills = mapOf(BotGearPurpose.MELEE to Skill.ATTACK, BotGearPurpose.RANGED to Skill.RANGED,
        BotGearPurpose.MAGIC to Skill.MAGIC)

    /**
     * Plans from actual ownership. A live match can only use its current weapon and physically carried supplies.
     */
    fun loadout(bot: Bot, purpose: BotGearPurpose, carriedOnly: Boolean = false): CombatLoadout? {
        if (bot.preferences.isSkiller || bot.preferences.scavenging.isIntroductory || purpose !in skills) return null
        val profile = bot.preferences.pk
        if (profile != null && !profile.styles().uses(skills.getValue(purpose))) return null
        val weapon = bot.equipment[Equipment.WEAPON]?.id
        if (carriedOnly && weapon == null) return null
        return CombatLoadout.find(bot, purpose, carriedOnly = carriedOnly,
            allowedWeapons = if (carriedOnly) setOf(weapon!!) else emptySet())?.takeIf {
            it.spell?.spell != CombatSpell.IBAN_BLAST || IbanStaff.remaining(bot) >= if (carriedOnly) 2 else 20
        }
    }

    /**
     * Checks each discipline's real controller rule without overriding the negotiated mask.
     */
    fun permitted(purpose: BotGearPurpose, rules: Int): Boolean = when (purpose) {
        BotGearPurpose.MELEE -> !DuelRules.NO_MELEE.enabled(rules)
        BotGearPurpose.RANGED -> !DuelRules.NO_RANGED.enabled(rules)
        BotGearPurpose.MAGIC -> !DuelRules.NO_MAGIC.enabled(rules)
        else -> false
    }

    /**
     * Computes a small optional wager before any coins enter escrow. Arbitrary-precision valuation avoids overflow.
     */
    fun stakeLimit(bot: Bot): Int {
        val wealth = PkRiskBudget.assess(bot, losesItems = false).wealth
        val affinity = if (bot.preferences.likesActivity(BotActivity.MINIGAMES)) 1.0 else 0.0
        val coins = bot.inventory.computeAmountForId(995).toLong() + bot.bank.computeAmountForId(995)
        return stakeLimit(wealth, coins, bot.personality.confidence, bot.personality.intelligence, affinity)
    }

    /**
     * Pure bound shared by initial planning and local overflow/personality verification.
     */
    internal fun stakeLimit(wealth: BigInteger, coins: Long, confidence: Double, intelligence: Double,
                            affinity: Double): Int {
        val points = PkRiskBudget.basisPoints(confidence, intelligence, affinity) / 10
        val share = wealth.multiply(BigInteger.valueOf(points.toLong())).divide(BigInteger.valueOf(10_000))
        return share.min(BigInteger.valueOf(minOf(100_000L, coins.coerceAtLeast(0) / 4))).toInt().coerceAtLeast(0)
    }
}
