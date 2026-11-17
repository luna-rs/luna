package game.bot.scripts

import api.bot.action.CombatLoadout
import api.bot.boss.BossFood
import api.bot.boss.BossTarget
import api.predef.*
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.npc.spawn.shantayPass.ShantayPass
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.bot.Bot

/**
 * Queen trip requirements. Melee can damage both forms; their icons are defence bonuses.
 * All supplies and gear must already be owned before profit combat can select this destination.
 *
 * @author lare96
 */
internal object KalphiteQueenTrip {
    const val POTION = 2434
    const val ROPE = 954
    val prayerPotions = setOf(2434, 139, 141, 143)
    val supplies = mapOf(POTION to 4, ROPE to 2, ShantayPass.PASS to 1)

    /**
     * Uses unboosted levels so a temporary potion cannot qualify an underlevelled bot.
     */
    fun levelsReady(bot: Bot, group: Boolean = false): Boolean = bot.combatLevel >= (if (group) 90 else 110) &&
        minOf(bot.attack.staticLevel, bot.strength.staticLevel, bot.defence.staticLevel,
            bot.hitpoints.staticLevel) >= (if (group) 70 else 85) && bot.prayer.staticLevel >= (if (group) 60 else 70)

    /**
     * Requires a strong melee weapon and substantial physical armour, independently of combat level.
     */
    fun gearReady(gear: Array<Int?>): Boolean {
        val weapon = gear[Equipment.WEAPON]?.let(::equipDef) ?: return false
        if ((0..2).maxOf { weapon.getBonus(it) } < 60 || weapon.getBonus(10) < 60) return false
        if (gear[Equipment.CHEST] == null || gear[Equipment.LEGS] == null) return false
        return (5..7).minOf { bonus -> gear.filterNotNull().sumOf { equipDef(it).getBonus(bonus) } } >= 180
    }

    /**
     * Full stock is needed for selection and fresh trips, including ropes even when another player tied them.
     */
    fun ready(bot: Bot, loadout: CombatLoadout, group: Boolean = false): Boolean = levelsReady(bot, group) && gearReady(loadout.gear) &&
        loadout.gear.filterNotNull().all { bot.itemTracker.contains(it) } &&
        supplies.all { (id, amount) -> bot.itemTracker.count(id) >= amount } &&
        BossFood.ownedReady(bot, BossTarget.KALPHITE_QUEEN, group)

    /**
     * Rechecks physical equipment after a pause, degradation or other script changes.
     */
    fun equipped(bot: Bot): Boolean = gearReady(Array(14) { bot.equipment[it]?.id })

    /**
     * Keeps a food reserve for getting out; the last potion dose may be consumed during a kill.
     */
    fun canContinue(bot: Bot, group: Boolean = false): Boolean = levelsReady(bot, group) && equipped(bot) &&
        BossFood.carriedHealing(bot, BossTarget.KALPHITE_QUEEN, group) >
            BossFood.reserveHealing(BossTarget.KALPHITE_QUEEN, group) &&
        (bot.prayer.level > 10 || prayerPotions.any { bot.inventory.contains(it) })
}
