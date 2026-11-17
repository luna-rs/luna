package api.bot.boss

import api.bot.action.CombatLoadout
import api.predef.*
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.ChaosElementalTrip
import game.bot.scripts.GiantMoleTrip
import game.bot.scripts.KalphiteQueenTrip
import game.player.item.consume.potion.Potion
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.bot.Bot

/**
 * Boss-specific admission shared by solo planning, recruitment and carried combat preparation.
 * Group admission lowers level floors and food budgets; every participant still supplies its own outfit and provisions.
 *
 * @author lare96
 */
internal object BossReadiness {
    /**
     * Real cure doses accepted for a dragon trip, strongest families first.
     */
    val poisonCures = listOf(Potion.ANTIDOTE_PLUS_PLUS, Potion.ANTIDOTE_PLUS,
        Potion.SUPER_ANTIPOISON, Potion.ANTIPOISON_POTION).flatMap { it.doses.reversed() }

    /**
     * Higher confidence accepts one companion; cautious bots require three. Humans never count.
     */
    fun requiredCompanions(confidence: Double): Int = when {
        !confidence.isFinite() -> 2
        confidence >= 0.7 -> 1
        confidence <= 0.3 -> 3
        else -> 2
    }

    /**
     * Uses the prospective participant's own confidence for recruitment and attendance checks.
     */
    fun requiredCompanions(bot: Bot): Int = requiredCompanions(bot.personality.confidence)

    /**
     * Rejects incompatible builds and low endurance independently of owned gear or temporary boosts.
     */
    fun levelsReady(bot: Bot, target: BossTarget, group: Boolean,
                    purpose: BotGearPurpose = BotGearPurpose.MELEE): Boolean {
        if (bot.preferences.isSkiller || purpose !in setOf(BotGearPurpose.MELEE,
                BotGearPurpose.RANGED, BotGearPurpose.MAGIC)) return false
        if (target in setOf(BossTarget.KBD, BossTarget.KALPHITE_QUEEN) && purpose != BotGearPurpose.MELEE)
            return false
        val offence = when (purpose) {
            BotGearPurpose.MELEE -> minOf(bot.attack.staticLevel, bot.strength.staticLevel)
            BotGearPurpose.RANGED -> bot.ranged.staticLevel
            else -> bot.magic.staticLevel
        }
        val readiness = minOf(bot.combatLevel, offence * 2 + 3)
        if (readiness < if (group) target.groupReadiness else target.soloReadiness) return false
        val hitpoints = when (target) {
            BossTarget.MOLE -> if (group) 45 else 60
            BossTarget.KBD -> if (group) 50 else 65
            BossTarget.CHAOS_ELEMENTAL -> if (group) 60 else 70
            BossTarget.KALPHITE_QUEEN -> if (group) 70 else 85
        }
        val minimumOffence = when (target) {
            BossTarget.MOLE -> if (group) 40 else 50
            BossTarget.KBD -> if (group) 50 else 60
            BossTarget.CHAOS_ELEMENTAL -> if (group) 55 else 65
            BossTarget.KALPHITE_QUEEN -> if (group) 70 else 85
        }
        return bot.hitpoints.staticLevel >= hitpoints && offence >= minimumOffence &&
            (target != BossTarget.KALPHITE_QUEEN || KalphiteQueenTrip.levelsReady(bot, group))
    }

    /**
     * Resolves the actual owned attack style, constraining KBD to a shield-compatible melee weapon.
     */
    fun loadout(bot: Bot, target: BossTarget, group: Boolean,
                purpose: BotGearPurpose = BotGearPurpose.MELEE): CombatLoadout? {
        if (!levelsReady(bot, target, group, purpose)) return null
        val selected = CombatLoadout.find(bot, purpose,
            requiredEquipment = if (target == BossTarget.KBD) mapOf(Equipment.SHIELD to 1540) else emptyMap(),
            minimumCasts = if (purpose == BotGearPurpose.MAGIC) 100 else 1) ?: return null
        if (selected.gear[Equipment.WEAPON] == null || selected.gear[Equipment.CHEST] == null ||
            selected.gear[Equipment.LEGS] == null ||
            selected.gear.filterNotNull().any { !bot.itemTracker.contains(it) }) return null
        if (purpose == BotGearPurpose.RANGED) {
            val ammunition = selected.gear[Equipment.AMMUNITION] ?: selected.gear[Equipment.WEAPON]!!
            if (bot.itemTracker.count(ammunition) < 100) return null
        }
        return selected
    }

    /**
     * Requires a complete fresh trip without creating, reserving or remotely purchasing any stock.
     */
    fun ready(bot: Bot, target: BossTarget, group: Boolean,
              purpose: BotGearPurpose = BotGearPurpose.MELEE): Boolean {
        val selected = loadout(bot, target, group, purpose) ?: return false
        if (target == BossTarget.KALPHITE_QUEEN) return KalphiteQueenTrip.ready(bot, selected, group)
        if (!BossFood.ownedReady(bot, target, group)) return false
        return when (target) {
            BossTarget.MOLE -> GiantMoleTrip.ready(bot, group)
            BossTarget.KBD -> bot.status.isAntiPoison() || poisonCures.any { bot.itemTracker.contains(it) }
            BossTarget.CHAOS_ELEMENTAL -> ChaosElementalTrip.ready(bot, group)
            else -> false
        }
    }
}
