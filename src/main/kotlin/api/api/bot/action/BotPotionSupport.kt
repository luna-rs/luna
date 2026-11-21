package api.bot.action

import api.predef.*
import engine.combat.prayer.CombatPrayer
import engine.combat.status.StatusEffectType
import game.player.item.consume.potion.Potion
import game.player.item.consume.potion.PotionFamily
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.bot.Bot
import kotlin.math.ceil

/**
 * Converts expected exposure and actual activity needs into dose quantities.
 *
 * @author lare96
 */
object BotPotionSupport {

    private val cures by lazy {
        listOf(Potion.ANTIPOISON_POTION to 150, Potion.SUPER_ANTIPOISON to 600,
            Potion.ANTIDOTE_PLUS to 1000, Potion.ANTIDOTE_PLUS_PLUS to 1200)
    }

    /**
     * Estimates Prayer depletion using the same drain and equipment resistance as the native prayer timer.
     */
    internal fun prayerDrain(prayers: Collection<CombatPrayer>, prayerBonus: Int, ticks: Int): Double {
        val resistance = (CombatPrayer.BASE_RESISTANCE + CombatPrayer.PRAYER_BONUS_BOOST * prayerBonus)
            .coerceAtLeast(CombatPrayer.BASE_RESISTANCE)
        return prayers.sumOf { it.drain }.toDouble() * ticks / resistance
    }

    fun prayerFloor(bot: Bot, prayers: Set<CombatPrayer>): Int? {
        val level = bot.prayer.staticLevel
        val active = prayers.filter { level >= it.level && it.drain > 0 }
        if (active.isEmpty()) return null
        val upcoming = ceil(prayerDrain(active, bot.equipment.getBonus(EquipmentBonus.PRAYER), 30)).toInt()
        val roomForDose = (level - Potion.prayerRestore(level) + 1).coerceAtLeast(1)
        return (upcoming + 1).coerceIn(1, roomForDose)
    }

    fun combatNeeds(bot: Bot, expectedTicks: Int, poison: Boolean, dragonfire: Boolean,
                    prayers: Set<CombatPrayer>, maximumCoins: Long, brews: Boolean = false,
                    prayerBonus: Int = bot.equipment.getBonus(EquipmentBonus.PRAYER),
                    restoreStats: Boolean = true): List<BotPotionTrip.Need> {
        if (expectedTicks < 1) return emptyList()
        val budget = maximumCoins.coerceAtLeast(0)
        return buildList {
            val poisonTicks = expectedTicks - maxOf(bot.status.remainingTicks(StatusEffectType.ANTIPOISON),
                bot.status.remainingTicks(StatusEffectType.FRUIT_ANTIPOISON))
            if (poison && poisonTicks > 0) {
                val tiers = cures.dropWhile { (_, ticks) -> ticks < poisonTicks.coerceAtMost(1200) }
                val choices = (tiers + cures).distinctBy { it.first }.map { it.first }
                val doses = ceil(poisonTicks.coerceAtMost(600) / 150.0).toInt()
                add(BotPotionTrip.Need(choices, doses, budget / doses))
            }
            val fireTicks = expectedTicks - bot.status.remainingTicks(StatusEffectType.ANTIFIRE)
            if (dragonfire && fireTicks > 0) {
                val doses = ceil(fireTicks / 600.0).toInt().coerceIn(1, 4)
                add(BotPotionTrip.Need(listOf(Potion.ANTI_FIRE_POTION), doses, budget / doses))
            }
            val prayerLevel = bot.prayer.staticLevel
            val drain = prayerDrain(prayers.filter { it.level <= prayerLevel }, prayerBonus, expectedTicks)
            val restore = Potion.prayerRestore(prayerLevel).coerceAtMost(prayerLevel.coerceAtLeast(1))
            val prayerDeficit = (ceil(drain).toInt() + restore / 2 - bot.prayer.level).coerceAtLeast(0)
            val prayerDoses = if (drain > 0) ceil(prayerDeficit.toDouble() / restore).toInt().coerceAtMost(8) else 0
            val brew = if (brews && stock(bot, Potion.SUPER_RESTORE) > 0L) {
                listOf(Potion.SARADOMIN_BREW, Potion.ZAMORAK_BREW).firstOrNull { stock(bot, it) > 0L }
            } else null
            if (brew != null) {
                add(BotPotionTrip.Need(listOf(Potion.SUPER_RESTORE), prayerDoses + 1, budget / (prayerDoses + 1)))
                add(BotPotionTrip.Need(listOf(brew), 1, 0, requires = setOf(Potion.SUPER_RESTORE)))
            } else if (prayerDoses > 0) {
                add(BotPotionTrip.Need(listOf(Potion.PRAYER_POTION, Potion.SUPER_RESTORE), prayerDoses,
                    budget / prayerDoses))
            } else if (restoreStats && drainedCombatStats(bot)) {
                add(BotPotionTrip.Need(listOf(Potion.RESTORE_POTION, Potion.SUPER_RESTORE), 1, budget))
            }
        }
    }

    fun drainedCombatStats(bot: Bot): Boolean =
        listOf(bot.attack, bot.strength, bot.defence, bot.ranged, bot.magic).any { it.level < it.staticLevel }

    fun skillingNeed(potion: Potion, doses: Int, maximumCoins: Long): BotPotionTrip.Need =
        BotPotionTrip.Need(listOf(potion), doses, maximumCoins / doses)

    fun skillingBudget(bot: Bot): Long =
        ((bot.inventory.computeAmountForId(995).toLong() + bot.bank.computeAmountForId(995)) / 100L)
            .coerceAtMost(80L)

    fun owns(bot: Bot, potion: Potion): Boolean = stock(bot, potion) > 0L

    private fun stock(bot: Bot, potion: Potion): Long {
        val family = PotionFamily.forPotion(potion)
        return family.ids.sumOf { id ->
            (bot.inventory.computeAmountForId(id).toLong() + bot.bank.computeAmountForId(id)) * family.doses(id)
        }
    }
}
