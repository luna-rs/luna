package game.bot.scripts

import api.bot.action.CombatLoadout
import api.predef.*
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.bot.Bot

/**
 * Queen trip requirements. Melee can damage both forms; their icons are defence bonuses.
 * All supplies and gear must already be owned before profit combat can select this destination.
 *
 * @author lare96
 */
internal object KalphiteQueenTrip {
    const val FOOD = 385
    const val POTION = 2434
    const val ROPE = 954
    val prayerPotions = setOf(2434, 139, 141, 143)
    val supplies = mapOf(FOOD to 20, POTION to 4, ROPE to 2)

    /**
     * Uses unboosted levels so a temporary potion cannot qualify an underlevelled bot.
     */
    fun levelsReady(bot: Bot): Boolean = bot.combatLevel >= 110 &&
        minOf(bot.attack.staticLevel, bot.strength.staticLevel, bot.defence.staticLevel,
            bot.hitpoints.staticLevel) >= 85 && bot.prayer.staticLevel >= 70

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
    fun ready(bot: Bot, loadout: CombatLoadout): Boolean = levelsReady(bot) && gearReady(loadout.gear) &&
        loadout.gear.filterNotNull().all { bot.itemTracker.contains(it) } &&
        supplies.all { (id, amount) -> bot.itemTracker.count(id) >= amount }

    /**
     * Rechecks physical equipment after a pause, degradation or other script changes.
     */
    fun equipped(bot: Bot): Boolean = gearReady(Array(14) { bot.equipment[it]?.id })

    /**
     * Keeps a food reserve for getting out; the last potion dose may be consumed during a kill.
     */
    fun canContinue(bot: Bot): Boolean = levelsReady(bot) && equipped(bot) &&
        bot.inventory.computeAmountForId(FOOD) > 2 &&
        (bot.prayer.level > 10 || prayerPotions.any { bot.inventory.contains(it) })
}
